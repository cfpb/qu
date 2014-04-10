(ns qu.data
  "This namespace contains all our functions for retrieving data from
MongoDB, including creating queries and light manipulation of the data
after retrieval."
  (:require [taoensso.timbre :as log]
            [clojure.string :as str]
            [clojure.walk :refer [postwalk]]
            [clojure.core.cache :as cache]            
            [qu.util :refer :all]
            [qu.logging :refer [log-with-time]]
            [qu.cache :as qc :refer [create-query-cache add-to-cache]]
            [qu.data.result :refer [->DataResult map->DataResult]]
            [qu.data.compression :as compression]
            [qu.data.definition :as definition]
            [qu.metrics :as metrics]
            [cheshire.core :as json]
            [cheshire.generate :refer [add-encoder encode-str]]
            [monger
             [core :as mongo :refer [with-db get-db]]
             [query :as q]
             [collection :as coll]
             [conversion :as conv]
             joda-time
             json]))

;; Prevent encoding regexes in JSON in the logs from throwing an error.
(add-encoder java.util.regex.Pattern encode-str)

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

(defn slice-columns
  "Slices are made up of dimensions, columns that can be queried, and
  metrics, which are columns, usually numeric, connected to a set of
  those dimensions. This function retrieves the names of all the
  columns, both dimensions and metrics."
  [slicedef]
  (concat (:dimensions slicedef) (:metrics slicedef)))

(defn concept-collection [concept]
  (str "concept__" (name concept)))

(defn concept-data
  "Get the data table for a concept."
  [dataset concept]
  (with-db (get-db dataset)
    (coll/find-maps (concept-collection concept))))

(defn field-zip-fn
  "Given a dataset and a slice, return a function that will compress
  field names."
  [dataset slice]
  (let [metadata (get-metadata dataset)
        slicedef (get-in metadata [:slices (keyword slice)])]
    (compression/field-zip-fn slicedef)))

(defn field-unzip-fn
  "Given a dataset and a slice, return a function that will decompress
  field names."
  [dataset slice]
  (let [metadata (get-metadata dataset)
        slicedef (get-in metadata [:slices (keyword slice)])]
    (compression/field-unzip-fn slicedef)))

(defn- text? [text]
  (or (string? text)
      (symbol? text)))

(defn get-find
  "Given a collection and a Mongo find map, return a Result of the form:
   :total - Total number of documents for the input query irrespective of skip or limit
   :size - Number of documents for the input query after skip and limit are applied
   :data - Seq of maps with the IDs stripped out"
  [database collection find-map]
  (metrics/with-timing "queries.find"
    (let [zipfn (field-zip-fn database collection)
          find-map (compression/compress-find find-map zipfn)
          unzipfn (field-unzip-fn database collection)]
      (log-with-time
       :info
       (str/join " " ["Mongo query"
                      (str database "/" (name collection))
                      (json/generate-string find-map)])
       (with-db (get-db database)
         (with-open [cursor (doto (coll/find collection (:query find-map) (:fields find-map))
                              (.limit (:limit find-map 0))
                              (.skip (:skip find-map 0))
                              (.sort (conv/to-db-object (:sort find-map))))]
           (->DataResult
            (.count cursor)
            (.size cursor)
            (->> cursor
                 (map (fn [x] (-> x
                                  (conv/from-db-object false)
                                  (convert-keys unzipfn)
                                  (dissoc :_id))))))))))))

(defn get-aggregation
  "Given a collection and a Mongo aggregation, return a Result of the form:
   :total - Total number of results returned
   :size - Same as :total
   :data - Seq of maps with the IDs stripped out

  After adding the compression processing, $match MUST come before $group."
  [database collection {:keys [query] :as aggmap}]
  (metrics/with-timing "queries.aggregation"
    (let [cache (create-query-cache)
          cache-record (if (cache/has? cache query)
                         (do (cache/hit cache query) nil)
                         (qc/add-to-queue cache aggmap))]
      (cache/lookup cache query
                    (map->DataResult {:computing (dissoc cache-record :aggmap)})))))

(defn get-data-table
  "Given retrieved data (a seq of maps) and the columns you want from
that data, return a seq of seqs representing the data in columnar
format."
  [data columns]
  (let [columns (map keyword columns)]
    (map (fn [row]
           (map (fn [column] (column row)) columns)) data)))
