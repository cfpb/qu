(ns cfpb.qu.loader2
  "This namespace contains all our functions for loading data from
datasets into MongoDB. This includes parsing CSV files and
transforming the data within."
  (:require
   [taoensso.timbre :as log]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.data.csv :as csv]
   [cheshire.core :as json]
   [com.stuartsierra.dependency :as dep]
   [drake.core :as drake]
   [clj-time.core :refer [default-time-zone]]
   [clj-time.format :as time]
   [monger
    [core :as mongo :refer [with-db get-db]]
    [query :as q]
    [collection :as coll]
    [joda-time]]
   [cfpb.qu.util :refer [->int ->num]]
   [cfpb.qu.query.where :as where]
   [cfpb.qu.data :refer :all])
  (:import [org.bson.types ObjectId]
           [com.mongodb MapReduceCommand$OutputType MapReduceOutput]))

(def ^:dynamic *chunk-size* 256)
(def default-date-parser (time/formatter (default-time-zone)
                                         "YYYY-MM-dd" "YYYY/MM/dd" "YYYYMMdd"))

(defn- run-drakefile
  [drakefile]
  (log/info "Running Drakefile" drakefile)
  ;; The following is Dark Drake Magic.
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

(defn- save-dataset-definition
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
  (->num (str/replace value #"^\$(-?\d+)" "$1")))

(defmethod cast-value "boolean" [value _]
  (cond
   (str/blank? value) nil
   (re-matches #"^[Ff]|[Nn]|[Nn]o|[Ff]alse$" (str/trim value)) false
   :default true))

(defmethod cast-value "date" [value {:keys [format]}]
  (if format
    (time/parse (time/formatter format) value)
    (time/parse default-date-parser value)))

(defmethod cast-value "lookup" [value {:keys [lookup]}]
  ;; Have to call keyword on value because Cheshire turns keys into keywords  
  (lookup (keyword value)))

(defmethod cast-value :default [value _]
  value)

(defn cast-data
  "Given the data from a CSV file and the definition of the data's columns,
transform that data into the form we want."
  [data columns]
  (reduce (fn [acc [column value]]
            (let [cdef (columns column)]
              (if (:skip cdef)
                acc
                (assoc acc
                  (keyword (or (:name cdef) column))
                  (cast-value value cdef)))))
          {} data))


(defn read-csv-file
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
        raw-data (->> sources
                      (map #(read-csv-file (str dir "/" %)))
                      (apply concat []))]
    (map #(cast-data % columns) raw-data)))

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

(defmulti read-slice
  (fn [slice definition]
    (get-in definition [:slices (keyword slice) :type])))

(defmethod read-slice "table"
  [slice definition]
  (let [table (get-in definition [:slices (keyword slice) :table])]
    (read-table table definition)))

(defmethod read-slice "derived"
  [slice definition]
  (let [database (:database definition)
        sdef (get-in definition [:slices (keyword slice)])
        from-collection (:slice sdef)        
        dimensions (:dimensions sdef)
        aggregations (:aggregations sdef)
        match (if-let [where (:where sdef)]
                (where/mongo-eval (where/parse where))
                {})        
        group-id (apply merge
                        (map #(hash-map % (str "$" %)) dimensions))
        aggs (map (fn [[agg-metric [agg metric]]]
                    {agg-metric {(str "$" (name agg))
                                 (str "$" (name metric))}}) aggregations)
        group (apply merge {:_id group-id} aggs)
        project-dims (map (fn [dimension]
                            {dimension (str "$_id." (name dimension))}) dimensions)
        project-aggs (map (fn [[agg-metric _]]
                            {agg-metric (str "$" (name agg-metric))}) aggregations)
        project (apply merge (concat project-dims project-aggs))
        aggregation [{"$group" group} {"$project" project} {"$match" match}]
        query-result (get-aggregation database from-collection aggregation)
        data (:data query-result)]
    (:data query-result)))

(defmethod read-slice :default
  [slice definition]
  (let [type (get-in definition [:slices (keyword slice) :type])]
    (log/error "Cannot load slice" slice "with type" type)))

(defn- join-maps
  "Join two vectors of maps on a key."
  [left lkey lval right rkey rval]
  (let [lkey (keyword lkey)
        lval (keyword lval)
        rkey (keyword rkey)
        rval (keyword rval)
        rmap (apply hash-map (flatten (map (juxt rkey rval) right)))]
    (->> left
         (map (fn [row]
                (assoc row lval (get rmap (get row lkey))))))))

(defn load-slice
  [slice concepts definition]
  (let [data (read-slice slice definition)
        references (get-in definition [:slices (keyword slice) :references])
        data (reduce (fn [data [column cdef]]
                       (join-maps data (:column cdef) column
                                  (get concepts (keyword (:concept cdef))) :_id (:value cdef)))
                     data references)
        chunks (partition-all *chunk-size* data)]
    (doseq [chunk chunks]
      (coll/insert-batch slice chunk))))
  
(defn load-slices
  [definition]
  (log/info "Loading concepts")  
  (let [concepts (read-concepts definition)
        slices (:slices definition)
        slice-graph (reduce (fn [graph [slice slicedef]]
                              (dep/depend graph slice (keyword (get-in slicedef [:slice] :top))))
                            (dep/graph)
                            slices)
        slice-load-order (remove #(= :top %) (dep/topo-sort slice-graph))]
      (doseq [slice slice-load-order]
        (log/info "Dropping slice" slice)
        (coll/drop slice)
        (log/info "Loading slice" slice)
        (load-slice slice concepts definition))))

(defn index-slice
  "Given a slice name and a dataset definition, create indexes for the slice."
  [slice definition]
  (log/info "Indexing slice" slice)
  (let [sdef (get-in definition [:slices (keyword slice)])
        indexes (or (:index_only sdef)
                    (:dimensions sdef))]
    (doseq [index indexes]
      (coll/ensure-index slice {(keyword index) 1}))))

(defn index-slices
  [definition]
  (let [slices (:slices definition)]
    (doseq [slice (keys slices)]
      (index-slice slice definition))))

(defn load-dataset
  "Given the name of a dataset, load that dataset from disk into the
  database. The dataset must be under the datasets/ directory as a
  directory containing a definition.json and a set of CSV files.

  These files are loaded in parallel."
  [dataset]
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

    (log/info "Loading dataset" dataset)

    (log/info "Saving definition for" dataset)
    (save-dataset-definition dataset definition)

    (when (and drakefile (.isFile drakefile))
      (run-drakefile drakefile))
    
    (with-db (get-db dataset)
      (log/info "Loading slices for dataset" dataset)
      (load-slices definition)
      (index-slices definition))))
