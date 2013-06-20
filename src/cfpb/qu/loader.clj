(ns cfpb.qu.loader
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

(defn- cast-value
  "Given a string value and a definition of that value, transform the
value into the correct type."
  [value valuedef]
  (let [value (str/trim value)]
    (case (:type valuedef)
      "integer" (->int value)
      "number"  (->num value)
      "dollars" (->num (str/replace value #"^\$(-?\d+)" "$1"))
      "boolean" (cond
                 (str/blank? value) nil
                 (re-matches #"^[Ff]|[Nn]|[Nn]o|[Ff]alse$" (str/trim value)) false
                 :default true)
      "date" (if-let [format (:format valuedef)]
               (time/parse (time/formatter format) value)
               (time/parse default-date-parser value))
      ;; Have to call keyword on value because Cheshire turns keys into keywords
      "lookup" ((:lookup valuedef) (keyword value))
      value)))

(defn- cast-data
  "Given the data from a CSV file and the definition of the data's columns,
transform that data into the data that will be saved in the database."
  [definition data]
  (into {}
        (remove nil?
                (for [[column value] data]
                  (let [columndef (definition column)]
                    (when-not (:skip columndef)
                      (vector (or (:name columndef) column)
                              (cast-value value columndef))))))))

(defn- set-indexes
  "Given a table name and a seq of indexes, create those indexes for the table."
  [table indexes]
  (doseq [index indexes]
    (coll/ensure-index table {(keyword index) 1})))

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

(defn- build-types-for-slice [slice definition]
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

(defn- build-types-for-slices
  "Add types to slice definitions based off the types from their
associated tables."
  [definition]
  (let [slices (keys (:slices definition))]
    (reduce (fn [definition slice]
              (build-types-for-slice slice definition))
            definition
            slices)))

(defn- save-dataset-definition
  "Save the definition of a dataset into the 'metadata' database."
  [name definition]
  (with-db (get-db "metadata")
    (coll/update "datasets" {:name name}
                 (build-types-for-slices (assoc definition :name name))
                 :upsert true)))

(defn load-csv-file
  "Given a table and CSV file name and the definitions of the table's
  columns, read the data from the CSV file, transform it, and insert
  it into the table.

  Steps:
  1. We read in the data from the CSV.
  2. We get the headers.
  3. We split the data into chunks of 100, and in parallel,
     transform each of those chunks into a map.
  4. We insert each of those chunks into the DB in a batch load."
  [collection file columns]
  (with-open [in-file (io/reader (io/resource file))]
    (let [data (csv/read-csv in-file)
          headers (map keyword (first data))
          chunks (->> (rest data)
                      (partition-all *chunk-size*)
                      (map (fn [chunk]
                             (doall
                              (map #(->> %
                                         (zipmap headers)
                                         (cast-data columns)) chunk)))))]
      (doseq [chunk chunks]
        (coll/insert-batch collection chunk)))))

(defn- load-collection
  [collection tabledef dir]
  (coll/drop collection)  
  (let [sources (:sources tabledef)
        columns (:columns tabledef)
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
                        (load-csv-file collection (str dir "/" file) columns))))
    (apply await agents)))

(defn- load-table-slice
  [dataset slice slicedef tables dir]
  (let [table (:table slicedef)
        tabledef ((keyword table) tables)
        collection (name slice)]
    (log/info "Loading table-backed slice" slice)
    (load-collection collection tabledef dir)))

(defn- load-derived-slice
  [dataset slice slicedef]
  (let [dimensions (:dimensions slicedef)
        aggregations (:aggregations slicedef)
        from-collection (:slice slicedef)
        to-collection slice
        match (if-let [where (:where slicedef)]
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
        query-result (get-aggregation dataset from-collection aggregation)
        chunks (->> query-result
                    :data
                    (partition-all *chunk-size*))]
    (coll/drop to-collection)
    (log/info "Loading derived slice" slice)
    (doseq [chunk chunks]
      (coll/insert-batch to-collection chunk))))

(defn- load-slice
  [dataset slice slicedef tables dir]
  (let [dimensions (:dimensions slicedef)
        type (keyword (:type slicedef))]
    (log/info "Dropping slice" slice)
    (case type
      :table (load-table-slice dataset slice slicedef tables dir)
      :derived (load-derived-slice dataset slice slicedef)
      (log/error "Cannot load slice" slice "with type" type))))

(defn- concept-collection [concept]
  (str "concept__" (name concept)))

(defn- load-concept
  "Go through each slice and replace any dimensions that match a
concept where the concept is backed by a table with the relevant row
from that table."
  [dataset concept definition slices tables dir]
  (if-let [table (:table definition)]
    (let [concept (keyword concept)
          tabledef ((keyword table) tables)
          collection (concept-collection concept)]
      (log/info "Loading table-backed concept" concept)
      (load-collection collection tabledef dir))))

(defn- set-reference-column
  [dataset slice column columndef concepts]
  (let [concept (keyword (:concept columndef))
        concept-def (concepts concept)
        collection (concept-collection concept)
        value (keyword (:value columndef))
        concept-data (->> (coll/find-maps collection)
                          (map (fn [row]
                                 [(:_id row)
                                  (value row)])))]
    (doseq [[key value] concept-data]
      (coll/update (name slice)
                   {(keyword (:column columndef)) key}
                   {"$set" {(keyword column) value}}
                   :multi true))))

(defn- add-concept-data
  "Go through each slice and add any dimensions that are references to
  concept data."
  [dataset slice slicedef concepts]
  (when-let [references (:references slicedef)]
    (log/info "Loading reference columns for" slice)
    (doseq [[column columndef] references]
      (log/info "Loading reference column" column)
      (set-reference-column dataset slice column columndef concepts))))

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
                       (json/parse-string true))
        tables (:tables definition)
        slices (:slices definition)
        concepts (:concepts definition)
        slice-graph (reduce (fn [graph [slice slicedef]]
                              (dep/depend graph slice (keyword (get-in slicedef [:slice] :top))))
                            (dep/graph)
                            slices)
        slice-load-order (remove #(= :top %) (dep/topo-sort slice-graph))]
    (log/info "Loading dataset" dataset)
    (log/info "Saving definition for" dataset)
    (save-dataset-definition dataset definition)

    (when (and drakefile (.isFile drakefile))
      (log/info "Running Drakefile")
      ;; The following is Dark Drake Magic.
      (drake/run-workflow drakefile :targetv ["=..."]))
    
    (with-db (get-db dataset)
      (doseq [[concept definition] concepts]
        (load-concept dataset concept definition slices tables dir))
      (doseq [slice slice-load-order]
        (log/info "Loading slice" (name slice) "for dataset" dataset)
        (load-slice dataset slice (slices slice) tables dir)
        (add-concept-data dataset slice (slices slice) concepts))
      (doseq [[slice definition] slices]
        (log/info "Indexing slice" slice)
        (set-indexes slice (or (:only-index definition)
                               (:dimensions definition)))))))

;; (ensure-mongo-connection)
;; (with-out-str (time (load-dataset "county_taxes")))
