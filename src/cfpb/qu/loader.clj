(ns cfpb.qu.loader
  "This namespace contains all our functions for loading data from
datasets into MongoDB. This includes parsing CSV files and
transforming the data within."
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure-csv.core :refer [parse-csv]]
   [cheshire.core :as json]
   [monger
    [core :as mongo :refer [with-db get-db]]
    [collection :as coll]]
   [cfpb.qu.data :refer :all])
  (:import [org.bson.types ObjectId]))

(defn- cast-value
  "Given a string value and a definition of that value, transform the
value into the correct type."
  [value valuedef]
  (case (:type valuedef)
    "integer" (Integer/parseInt value)
    "dollars" (Integer/parseInt (str/replace value #"^\$(-?\d+)" "$1"))
    ;; Have to call keyword on value because Cheshire turns keys into keywords
    "lookup" ((:lookup valuedef) (keyword value))
    value))

(defn- cast-data
  "Given the data from a CSV file and the definition of the data's columns,
transform that data into the data that will be saved in the database."
  [data definition]
  (into {}
        (remove nil?
                (for [[column value] data]
                  (let [columndef (definition column)]
                    (if (not (:skip columndef))
                      [(or (:name columndef) column)
                       (cast-value value columndef)]))))))

(defn- load-csv-file
  "Given a table and CSV file name and the definitions of the table's
columns, read the data from the CSV file, transform it, and insert it
into the table."
  [table file columns]
  (with-open [in-file (io/reader (io/resource file))]
    (let [data (parse-csv in-file)
          headers (map keyword (first data))
          data (map #(zipmap headers %) (rest data))]
      (doseq [datum data]    
        (coll/insert table (cast-data datum columns))))))

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
    "dollars" "integer"
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

(defn- get-indexes
  "Given the definition of the dataset's slices and the current table
being imported, return a seq of the columns that should be indexed in
that table."
  [slices table]
  (->> slices
       (map (fn [[_ slice]]
         (if (= (:table slice) (name table))
           (:dimensions slice)
           [])))
       flatten))

(defn load-dataset
  "Given the name of a dataset, load that dataset from disk into the
database. The dataset must be under the datasets/ directory as a
directory containing a definition.json and a set of CSV files."
  [name]
  (let [dir (str "datasets/" name)
        definition (-> (str dir "/definition.json")
                       io/resource
                       slurp
                       (json/parse-string true))
        tables (:tables definition)
        slices (:slices definition)]
    (save-dataset-definition name definition)
    (with-db (get-db name)
      (doseq [table (keys tables)]
        (coll/remove table)
        (let [tabledef (table tables)
              indexes (get-indexes slices table)
              sources (:sources tabledef)
              columns (:columns tabledef)]
          (set-indexes table indexes)
          (doseq [file sources]
            (load-csv-file table (str dir "/" file) columns)))))))

(defn- pload-csv-file
  "Given a table and CSV file name and the definitions of the table's
columns, read the data from the CSV file, transform it, and insert it
into the table."
  [table file columns]
  (with-open [in-file (io/reader (io/resource file))]
    (let [data (parse-csv in-file)
          headers (map keyword (first data))
          data (map #(zipmap headers %) (rest data))
          data (partition 1000 data)
          agents (map agent data)]
      (doseq [agent agents]
        (send-off agent (fn [data]
                          (doseq [datum data]
                            (coll/insert table (cast-data datum columns))))))
      (apply await-for 100000 agents))))

(defn pload-dataset
  "Given the name of a dataset, load that dataset from disk into the
database. The dataset must be under the datasets/ directory as a
directory containing a definition.json and a set of CSV files."
  [name]
  (let [dir (str "datasets/" name)
        definition (-> (str dir "/definition.json")
                       io/resource
                       slurp
                       (json/parse-string true))
        tables (:tables definition)
        slices (:slices definition)]
    (save-dataset-definition name definition)
    (with-db (get-db name)
      (doseq [table (keys tables)]
        (coll/remove table)
        (let [tabledef (table tables)
              indexes (get-indexes slices table)
              sources (:sources tabledef)
              columns (:columns tabledef)
              agents (map agent sources)]
          (set-indexes table indexes)
          (doseq [agent agents]
            (send-off agent (fn [file]
                              (load-csv-file table (str dir "/" file) columns))))
          (apply await-for 100000 agents))))))

; (ensure-mongo-connection)
; (with-out-str (time (load-dataset "county_taxes")))
; (with-out-str (time (pload-dataset "county_taxes")))
