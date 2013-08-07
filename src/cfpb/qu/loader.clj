(ns cfpb.qu.loader
  "This namespace contains all our functions for loading data from
datasets into MongoDB. This includes parsing CSV files and
transforming the data within."
  (:require
   [taoensso.timbre :as log]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.data.csv :as csv]
   [clojure.core.reducers :as r]
   [cheshire.core :as json]
   [com.stuartsierra.dependency :as dep]
   [drake.core :as drake]
   [clj-time.core :refer [default-time-zone]]
   [clj-time.format :as time]
   [lonocloud.synthread :as ->]
   [monger
    [core :as mongo :refer [with-db get-db]]
    [query :as q]
    [collection :as coll]
    [joda-time]]
   [cfpb.qu.util :refer :all]
   [cfpb.qu.query.where :as where]
   [cfpb.qu.data :refer :all])
  (:import [org.bson.types ObjectId]))

(def ^:dynamic *chunk-size* 256)
(def default-date-parser (time/formatter (default-time-zone)
                                         "YYYY-MM-dd" "YYYY/MM/dd" "YYYYMMdd"))

(defn run-drakefile
  "Run all tasks in a Drakefile."
  [drakefile]
  (log/info "Running Drakefile" drakefile)
  (drake/run-workflow drakefile :targetv ["=..."]))

(defn- slice-type-for-table-type
  "The types for transforming data from the strings in the CSV file
and the types for retrieval from the database are not identical. Given
a type from the column definition, this function returns the type used
in MongoDB."
  [type]
  (case type
    "integer" "integer"
    "dollars" "number"
    "number" "number"
    "string"))

(defn save-dataset-definition
  "Save the definition of a dataset into the 'metadata' database."
  [name definition]
  (with-db (get-db "metadata")
    (letfn [(build-types-for-slice [slice definition]
              (let [slice-def (get-in definition [:slices slice])
                    table-name (:table slice-def)
                    table-def (get-in definition [:tables (keyword table-name)])]
                (reduce (fn [definition column]
                          (if (and (:name column)
                                   ((set (slice-columns slice-def)) (:name column)))
                            (assoc-in definition
                                      [:slices slice :types (keyword (:name column))]
                                      (slice-type-for-table-type (:type column)))
                            definition))
                        definition
                        (vals (:columns table-def)))))
            (build-types-for-slices [definition]
              (let [slices (keys (:slices definition))]
                (reduce (fn [definition slice]
                          (build-types-for-slice slice definition))
                        definition
                        slices)))]
      (coll/update "datasets" {:name name}
                   (build-types-for-slices (assoc definition :name name))
                   :upsert true))))

(defmulti cast-value (fn [_ valuedef] (:type valuedef)))

(defmethod cast-value "integer" [value _]
  (->int value))

(defmethod cast-value "number" [value _]
  (->num value))

(defmethod cast-value "dollars" [value _]
  (when-not (str/blank? value)
    (->num (str/replace value #"^\$(-?\d+)" "$1"))))

(defmethod cast-value "boolean" [value _]
  (cond
   (str/blank? value) nil
   (re-matches #"^[Ff]|[Nn]|[Nn]o|[Ff]alse$" (str/trim value)) false
   :default true))

(defmethod cast-value "date" [value {:keys [format]}]
  (cond
   (str/blank? value) nil
   format (time/parse (time/formatter format) value)
   :default (time/parse default-date-parser value)))

(defmethod cast-value "lookup" [value {:keys [lookup]}]
  ;; Have to call keyword on value because Cheshire turns keys into keywords
  (when-not (str/blank? value)
    (lookup (keyword value))))

(defmethod cast-value :default [value _]
  value)

(defn cast-data
  "Given the data from a CSV file and the definition of the data's columns,
transform that data into the form we want."
  [data columns]
  (into {}
        (r/remove nil?
                  (r/map (fn [[column value]]
                           (let [cdef (columns column)]
                             (when-not (or (nil? cdef)
                                           (:skip cdef))
                               [(keyword (or (:name cdef) column))
                                (cast-value value cdef)]))) data))))

(defn read-csv-file
  "Reads an entire CSV file into memory as a seq of maps.
  The seq is fully realized because we have to in order to
  close the CSV. Therefore, this can suck up a lot of memory
  and is only recommended for small CSV files."
  [file]
  (with-open [in-file (io/reader (io/resource file))]
    (let [data (csv/read-csv in-file)
          headers (map keyword (first data))]
      (map (partial zipmap headers) (doall (rest data))))))

(defn read-table
  [table definition]
  (let [tdef (get-in definition [:tables (keyword table)])
        dir (:dir definition)
        sources (:sources tdef)
        columns (:columns tdef)
        file-data (->> sources
                       (map #(read-csv-file (str dir "/" %)))
                       (map (fn [d]
                              (map #(cast-data % columns) d))))]
    (reduce concat file-data)))

(defn read-concept-data
  [concept definition]
  (let [cdef (get-in definition [:concepts concept])
        tables (:tables definition)]
    (when-let [table (:table cdef)]
      (read-table table definition))))

(defn read-concepts
  [definition]
  (let [concepts (:concepts definition)]
    (reduce (fn [acc concept]
              (assoc acc concept (read-concept-data concept definition)))
            {} (keys concepts))))

(defn- join-maps
  "Join two vectors of maps on a key."
  [left lkey lval right rkey rval]
  (let [lkey (keyword lkey)
        lval (keyword lval)
        rkey (keyword rkey)
        rval (keyword rval)
        rmap (apply hash-map (flatten (map (juxt rkey rval) right)))]
    (map (fn [row]
             (assoc row lval (get rmap (get row lkey)))) left)))

(defn load-csv-file
  [file collection transform-fn]
  (with-open [in-file (io/reader (io/resource file))]
    (let [csv (csv/read-csv in-file)
          headers (map keyword (first csv))
          data (->> (rest csv)
                    (map (partial zipmap headers))
                    transform-fn)
          chunks (partition-all *chunk-size* data)]
      (doseq [chunk chunks]
        (coll/insert-batch collection chunk)))))

(defn load-table
  [table collection concepts references definition & {:keys [keyfn]}]
  (let [tdef (get-in definition [:tables (keyword table)])
        dir (:dir definition)
        sources (:sources tdef)
        columns (:columns tdef)        
        cast-data (partial map #(cast-data % columns))
        add-concepts (fn [data]
                       (reduce (fn [data [column cdef]]
                                 (join-maps
                                  data (:column cdef) column
                                  (get concepts (keyword (:concept cdef)))
                                  :_id (:value cdef)))
                               data references))
        transform-keys (if keyfn
                         (partial map #(convert-keys % keyfn))
                         identity)
        remove-nils (fn [data]
                      (map
                       (fn [row]
                         (into {} (remove (fn [[k v]] (nil? v)) row)))
                       data))
        transform-fn (comp remove-nils transform-keys add-concepts cast-data)
        agent-error-handler (fn [agent exception]
                              (log/error "Error in table loading agent"
                                         (.getMessage exception)))
        agents (map (fn [source]
                      (agent source
                             :error-mode :continue
                             :error-handler agent-error-handler))
                    sources)]
    (doseq [agent agents]
      (send-off agent (fn [file]
                        (load-csv-file (str dir "/" file) collection transform-fn))))
    (apply await agents)))

(defmulti load-slice
  (fn [slice _ definition]
    (get-in definition [:slices (keyword slice) :type])))

(defmethod load-slice "table"
  [slice concepts definition]
  (let [sdef (get-in definition [:slices (keyword slice)])
        table (:table sdef)
        references (:references sdef)
        zipfn (field-zip-fn sdef)]
    (load-table table slice concepts references definition :keyfn zipfn)))

(defmethod load-slice "derived"
  [slice concepts definition]
  (let [database (:database definition)
        sdef (get-in definition [:slices (keyword slice)])
        from-collection (:slice sdef)
        dimensions (:dimensions sdef)
        aggregations (:aggregations sdef)
        match (if-let [where (:where sdef)]
                (where/mongo-eval (where/parse where))
                {})        
        group-id (apply merge
                        (map #(hash-map % (str "$" (name %))) dimensions))
        aggs (map (fn [[agg-metric [agg metric]]]
                    {agg-metric {(str "$" (name agg))
                                 (str "$" (name metric))}}) aggregations)
        group (apply merge {:_id group-id} aggs)
        project-dims (map (fn [dimension]
                            {dimension
                             (str "$_id." (name dimension))}) dimensions)
        project-aggs (map (fn [[agg-metric _]]
                            {agg-metric
                             (str "$" (name agg-metric))}) aggregations)
        project (apply merge (concat project-dims project-aggs))
        aggregation [{"$group" group} {"$project" project} {"$match" match}]
        query-result (get-aggregation database from-collection aggregation)
        data (-> query-result
                 :data
                 (convert-keys (field-zip-fn sdef)))
        chunks (partition-all *chunk-size* data)]
    (doseq [chunk chunks]
      (coll/insert-batch slice chunk))))

(defmethod load-slice :default
  [slice concepts definition]
  (let [type (get-in definition [:slices (keyword slice) :type])]
    (log/error "Cannot load slice" slice "with type" type)))

(defn index-slice
  "Given a slice name and a dataset definition, create indexes for the slice."
  [slice definition]
  (log/info "Indexing slice" slice)
  (let [sdef (get-in definition [:slices (keyword slice)])
        indexes (or (:index_only sdef)
                    (:dimensions sdef))
        zipfn (field-zip-fn sdef)]
    (doseq [index indexes]
      (coll/ensure-index slice {(zipfn index) 1}))))

(defn load-slices
  ([definition]
     (log/info "Loading concepts")
     (load-slices (read-concepts definition) definition))  
  ([concepts definition]
     (let [slices (:slices definition)
           slice-graph (reduce (fn [graph [slice slicedef]]
                                 (dep/depend graph slice (keyword (get-in slicedef [:slice] :top))))
                               (dep/graph)
                               slices)
           slice-load-order (remove #(= :top %) (dep/topo-sort slice-graph))]
       (doseq [slice slice-load-order]
         (log/info "Dropping slice" slice)
         (coll/drop slice)
         (log/info "Loading slice" slice)
         (load-slice slice concepts definition)
         (index-slice slice definition)))))

(defn write-concept-data
  [concepts]
  (doseq [[concept data] concepts]
    (when (seq data)
      (let [collection (concept-collection concept)]
        (coll/drop collection)
        (coll/insert-batch collection data)))))

(defn load-dataset
  "Given the name of a dataset, load that dataset from disk into the
  database. The dataset must be under the datasets/ directory as a
  directory containing a definition.json and a set of CSV files.

  These files are loaded in parallel."
  [dataset & {:keys [delete]}]
  (log/info "Loading dataset" dataset)
  (let [dataset (name dataset)
        dir (str "datasets/" dataset)
        drakefile (-> (str dir "/Drakefile")
                      (io/resource)
                      (io/as-file))
        definition (-> (str dir "/definition.json")
                       io/resource
                       slurp
                       (json/parse-string true)
                       (assoc :dir dir)
                       (assoc :database dataset))]
    (when delete
      (log/info "Dropping old dataset" dataset)
      (mongo/drop-db dataset))

    (log/info "Saving definition for" dataset)
    (save-dataset-definition dataset definition)

    (when (and drakefile (.isFile drakefile))
      (run-drakefile drakefile))

    (let [concepts (read-concepts definition)]
      (with-db (get-db dataset)
        (log/info "Writing concept data")
        (write-concept-data concepts)
        (log/info "Loading slices for dataset" dataset)
        (load-slices concepts definition)))))
