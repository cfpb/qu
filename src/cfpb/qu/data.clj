(ns cfpb.qu.data
  "This namespace contains all our functions for retrieving data from
MongoDB, including creating queries and light manipulation of the data
after retrieval."
  (:require [clojure.string :as str]
            [monger
             [core :as mongo :refer [with-db get-db]]
             [query :as q]
             [collection :as coll]
             json]))

(defn get-datasets
  "Get metadata for all datasets. Information about the datasets is
stored in a Mongo database called 'metadata'."
  []
  (with-db (get-db "metadata")
    (coll/find-maps "datasets" {})))

(defn get-dataset-names
  "List all datasets."
  []
  (map :name (get-datasets)))

(defn get-metadata
  "Get metadata for one dataset."
  [dataset]
  (with-db (get-db "metadata")
    (coll/find-one-as-map "datasets" {:name dataset})))

(defn concept-description
  "Each dataset has a list of concepts. A concept is a definition of a
  type of data in the dataset. This function retrieves the description
  of the concept."
  [metadata concept]
  (get-in metadata [:concepts (keyword concept) :description] concept))

(defn slice-columns
  "Slices are made up of dimensions, columns that can be queried, and
  metrics, which are columns, usually numeric, connected to a set of
  those dimensions. This function retrieves the names of all the
  columns, both dimensions and metrics."
  [slice-def]
  (concat (:dimensions slice-def) (:metrics slice-def)))

(defn select-fields
  "In API requests, the user can select the columns they want
  returned. If they choose to do this, the columns will be in a
  comma-separated string. This function returns a seq of column names
  from that string."
  [select]
  (if select
    (str/split select #",\s*")))

(defn order-by-sort
  "In API requests, the user can select the order of data returned. If
  they do this, the order will be specified as a comma-separated
  string like so: 'column [desc], column, ...'. This function takes
  that string and returns a sorted map consisting of columns as keys
  and -1/1 for values depending on whether the sort is descending or
  ascending for that column."
  [order-by]
  (if order-by
    (->> (str/split order-by #",\s*")
         (map (fn [order]
                (let [order (str/split order #"\s+")]
                  ;; TODO refactor
                  (if (= (count order) 2)
                    (vector (first order)
                            (if (= "desc" (str/lower-case (second order)))
                              -1
                              1))
                    (vector (first order) 1)))))
         flatten
         (apply sorted-map))))

(defn get-data
  "Given the definition of a slice (from the dataset's metadata) and a
  map with the queried dimensions and other clauses for the request,
  return the queried data from the slice.

  $where and $group are currently not supported clauses, although
  their presence will cause no errors."
  [slice {:keys [dimensions clauses]}]
  (let [table (:table slice)
        columns (slice-columns :table)
        fields (or (select-fields (:$select clauses))
                   (slice-columns :table))
        ;; only call Integer/parseInt if giving a string
        limit (Integer/parseInt (:$limit clauses "100"))
        offset (Integer/parseInt (:$offset clauses "0"))
        sort (or (order-by-sort (:$orderBy clauses))
                 {})
        ; add $where
        ; add $group
        ]
    (map #(dissoc % :_id)
         (q/with-collection table
           (q/find dimensions)
           (q/fields fields)
           (q/limit limit)
           (q/skip offset)
           (q/sort sort)))))

(defn get-data-table
  "Given retrieved data (a seq of maps) and the columns you want from
that data, return a seq of seqs representing the data in columnar
format."
  [data columns]
  (map (fn [row]
         (map (fn [column]
                (str (row (keyword column)))) columns)) data))

