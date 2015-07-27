(ns qu.loader
  "This namespace contains all our functions for loading data from
datasets into MongoDB. This includes parsing CSV files and
transforming the data within."
  (:require [clj-time.core :refer [default-time-zone now]]
            [clj-time.format :as time]
            [clojure.core.reducers :as r]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.stuartsierra.dependency :as dep]
            [drake.core :as drake]
            [monger.collection :as coll]
            [monger.core :as mongo :refer [get-db with-db]]
            [monger.key-compression :refer [compression-map]]
            [qu.data :as data]
            [qu.data.aggregation :refer [generate-agg-query]]
            [qu.data.compression :refer [field-zip-fn]]
            [qu.data.definition :as definition]
            [qu.query.where :as where]
            [qu.util :refer :all]
            [taoensso.timbre :as log]))

;; Needed to interact with joda-time.
(require 'monger.joda-time)


(def ^:dynamic *chunk-size* 256)
(def default-date-parser (time/formatter (default-time-zone)
                                         "YYYY-MM-dd" "YYYY/MM/dd" "YYYYMMdd"))

(defn- run-drakefile
  "Run all tasks in a Drakefile."
  [drakefile]
  (log/info "Running Drakefile" drakefile)
  (let [options (apply concat
                       (merge drake/DEFAULT-OPTIONS
                              {:targetv ["=..."]}))]
    (apply drake/run-workflow drakefile options)))

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

(defn- build-types-for-slices
  [definition]
  (letfn [(build-types-for-slice [slice definition]
                                 (let [slice-def (get-in definition [:slices slice])
                                       table-name (:table slice-def)
                                       table-def (get-in definition [:tables (keyword table-name)])]
                                   (reduce (fn [definition column]
                                             (if (and (:name column)
                                                      ((set (data/slice-columns slice-def)) (:name column)))
                                               (assoc-in definition
                                                         [:slices slice :types (keyword (:name column))]
                                                         (slice-type-for-table-type (:type column)))
                                               definition))
                                           definition
                                           (vals (:columns table-def)))))]
    (let [slices (keys (:slices definition))]
      (reduce (fn [definition slice]
                (build-types-for-slice slice definition))
              definition
              slices))))

(defn read-definition
  "Read the definition of a dataset from disk."
  [dataset]
  (let [dataset (name dataset)
        dir (str "datasets/" dataset)]
    (-> (str dir "/definition.json")
        io/resource
        definition/read-definition
        (assoc :dir dir)
        (assoc :database dataset))))

(defn save-dataset-definition
  "Save the definition of a dataset into the 'metadata' database."
  [name definition]
  (let [definition (-> definition
                       build-types-for-slices
                       (assoc :name name :last-modified (now))
                       (dissoc :tables))
        definition (reduce (fn [def [slice sdef]]
                             (assoc-in def
                                       [:compression-map slice]
                                       (compression-map (data/slice-columns sdef))))
                           definition
                           (:slices definition))]
    (with-db (get-db "metadata")
             (coll/update "datasets" {:name name}
                          definition
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

(defn- cast-data
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

(defn- read-csv-file
  "Reads an entire CSV file into memory as a seq of maps.
  The seq is fully realized because we have to in order to
  close the CSV. Therefore, this can suck up a lot of memory
  and is only recommended for small CSV files."
  [file]
  (let [res (io/resource file)]
    (assert res (str file " should exist but does not"))
    (with-open [in-file (io/reader res)]
      (let [data (csv/read-csv in-file)
            headers (map keyword (first data))]
        (map (partial zipmap headers) (doall (rest data)))))))

(defn- read-table
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

(defn- read-concept-data
  [concept definition]
  (let [cdef (get-in definition [:concepts concept])
        tables (:tables definition)]
    (when-let [table (:table cdef)]
      (read-table table definition))))

(defn- read-concepts
  [definition]
  (let [concepts (:concepts definition)]
    (reduce (fn [acc concept]
              (assoc acc concept (read-concept-data concept definition)))
            {} (keys concepts))))

(defn- emit-post-load-sample
  [dataset slice]
  (let [result (data/get-find dataset slice {:limit 1 :query {} :fields {}})]
    (log/info "Slice loaded. " (:total result)  "records found. Sample record:" (:data result))
    ))

(defn- join-maps
  "Join two vectors of maps on some keys."
  [lmaps lkeys lvals rmaps rkeys rvals]
  {:pre [(= (count lkeys) (count rkeys))
         (= (count lvals) (count rvals))]}
  (let [get-lkeys (apply juxt lkeys)
        get-rkeys (apply juxt rkeys)
        get-rvals (apply juxt rvals)
        joinmap (reduce (fn [m row]
                          (assoc m (get-rkeys row) (get-rvals row)))
                        {} rmaps)]
    (map (fn [row]        
           (let [joindata (zipmap lvals (get joinmap (get-lkeys row)))]
             (merge row joindata))) lmaps)))

(defn- load-csv-file
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
                                  data
                                  (map keyword (coll-wrap (:column cdef)))
                                  (map keyword (coll-wrap column))
                                  (get concepts (keyword (:concept cdef)))
                                  (map keyword (coll-wrap (:id cdef :_id)))
                                  (map keyword (coll-wrap (:value cdef)))))
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
                                         agent (.getMessage exception)))
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

(defn derived-slice-agg-query
  [slice definition]
  (let [sdef (get-in definition [:slices (keyword slice)])
        dataset (:database definition)

        from-collection (:slice sdef)
        from-slicedef (get-in definition [:slices (keyword from-collection)])
        to-collection (name slice)
        to-zip-fn (field-zip-fn sdef)

        dimensions (:dimensions sdef)
        aggregations (:aggregations sdef)
        filter (if-let [where (:where sdef)]
                 (-> where
                     (where/parse)
                     (where/mongo-eval))
                 {})]
    (generate-agg-query {:dataset dataset
                         :from from-collection
                         :to to-collection
                         :group dimensions
                         :aggregations aggregations
                         :filter filter
                         :slicedef from-slicedef})))

(defmethod load-slice "derived"
           [slice concepts definition]
  (let [agg-query (derived-slice-agg-query slice definition)
        slicedef (get-in definition [:slices (keyword slice)])
        from-collection (:slice slicedef)        
        fields (data/slice-columns slicedef)
        zip-fn (field-zip-fn slicedef)
        rename-map (reduce (fn [acc field]
                             (merge acc {field (zip-fn field)})) {} fields)
        agg-results (mongo/command (sorted-map :aggregate from-collection :pipeline agg-query :allowDiskUse true))]
    (log/info "Aggregation for " slice agg-query)
    (log/info "Results of aggregation for" slice agg-results)
    ;; Convert field names to compressed fields
    (coll/update slice {} {"$rename" rename-map} :multi true)))

(defmethod load-slice :default
           [slice concepts definition]
  (let [type (get-in definition [:slices (keyword slice) :type])]
    (log/error "Cannot load slice" slice "with type" type)))

(defn index-slice
  "Given a slice name and a dataset definition, create indexes for the slice."
  [slice definition]
  (log/info "Indexing slice" slice)
  (let [sdef (get-in definition [:slices (keyword slice)])
        indexes (or (:indexes sdef)
                    (:index_only sdef) ; deprecated
                    (:dimensions sdef))
        zipfn (field-zip-fn sdef)]
    (doseq [index indexes]
      (if (sequential? index)
        (let [index-map (apply array-map (interleave (map zipfn index) (repeat 1)))]
          (coll/ensure-index slice index-map))
        (coll/ensure-index slice {(zipfn index) 1})))))

(defn- load-slices
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
       (index-slice slice definition)
       (emit-post-load-sample (:database definition) slice)))))

(defn- write-concept-data
  [concepts]
  (doseq [[concept data] concepts]
    (when (seq data)
      (let [collection (data/concept-collection concept)]
        (coll/drop collection)
        (coll/insert-batch collection data)))))

(defn load-dataset
  "Given the name of a dataset, load that dataset from disk into the
  database. The dataset must be under the datasets/ directory as a
  directory containing a definition.json and a set of CSV files.

  These files are loaded in parallel."
  [dataset & {:keys [delete] :or {delete true}}]
  (log/info "Loading dataset" dataset)
  ;; TODO show better error msg on failure to find dataset
  (let [dataset (name dataset)
        definition (read-definition dataset)
        dir (:dir definition)
        drakefile (-> (str dir "/Drakefile")
                      (io/resource)
                      (io/as-file))]
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

(defn ez-prepare-data
  "Prepare all data you need for a dataset."
  [dataset]
  (let [dataset (name dataset)
        definition (read-definition dataset)
        dir (:dir definition)
        drakefile (-> (str dir "/Drakefile")
                      (io/resource)
                      (io/as-file))]
    (when (and drakefile (.isFile drakefile))
      (run-drakefile drakefile))))

(defn ez-load-definition
  "Load the definition of a dataset.
  Does not run Drake to process data first."
  [dataset]
  (let [dataset (name dataset)
        definition (read-definition dataset)]
    (log/info "Saving definition for" dataset)
    (save-dataset-definition dataset definition)))

(defn ez-load-concepts
  "Load just the concepts for a dataset.
  Does not run Drake to process data first."
  [dataset]
  (let [dataset (name dataset)
        definition (read-definition dataset)
        concepts (read-concepts definition)]
    (with-db (get-db dataset)
             (log/info "Writing concept data")
             (write-concept-data concepts))))

(defn ez-load-ref-column
  [dataset slice refcol]
  (ez-load-concepts dataset)
  (with-db (get-db dataset)
           (let [dataset (name dataset)
                 definition (read-definition dataset)
                 concepts (read-concepts definition)
                 slicedef (get-in definition [:slices (keyword slice)])
                 refdef (get-in slicedef [:references (keyword refcol)])
                 zipfn (field-zip-fn slicedef)
                 concept (:concept refdef)
                 id (keyword (:id refdef))
                 slice-cols (->> (:column refdef)
                                 (coll-wrap)
                                 (map keyword)
                                 (map zipfn))
                 concept-cols (->> (:id refdef :_id)
                                   (coll-wrap)
                                   (map keyword))
                 value (keyword (:value refdef))
                 concept-data (->> (data/concept-collection concept)
                                   (coll/find-maps)
                                   (map (fn [row]
                                          [(into {}
                                                 (zipmap slice-cols
                                                         ((apply juxt concept-cols) row)))
                                           {(zipfn refcol) (value row)}])))]
             (log/info "Writing" refcol "for" dataset slice)
             (log/info concept-data)
             (doseq [[find-map update-map] concept-data]
               (coll/update (name slice)
                            find-map
                            {"$set" update-map}
                            :multi true)))))

(defn ez-load-slice
  ([dataset slice]
   (ez-load-slice dataset slice false))
  ([dataset slice delete]
   "Load one slice for a dataset.
   Does not run Drake to process data first. Also does not run other
   slices that the slice may depend on, so make sure you have all the
   data you need before running this. True to drop any existing data in
   the dataset prior to loading; false to skip deletion (default is false)."
   (let [dataset (name dataset)
         definition (read-definition dataset)
         concepts (read-concepts definition)]
     (with-db (get-db dataset)
              (when delete
                (log/info "Dropping slice" slice)
                (coll/drop slice))
              (log/info "Loading slice" slice)
              (load-slice slice concepts definition)
              (index-slice slice definition)
              (emit-post-load-sample dataset slice)))))

(defn ez-index-slice
  [dataset slice]
  "Given a dataset name and slice name, create indexes for the slice."
  (let [definition (read-definition dataset)]
    (with-db (get-db dataset)
             (index-slice slice definition))))
