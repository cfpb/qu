(ns cfpb.qu.data
  "This namespace contains all our functions for retrieving data from
MongoDB, including creating queries and light manipulation of the data
after retrieval."
  (:require [taoensso.timbre :as log]
            [clojure.string :as str]
            [environ.core :refer [env]]
            [monger
             [core :as mongo :refer [with-db get-db]]
             [query :as q]
             [collection :as coll]
             [conversion :as conv]
             [key-compression :as mzip]
             joda-time
             json]))

(defn connect-mongo []
  (let [address (mongo/server-address (env :mongo-host)  (Integer/parseInt(str (env :mongo-port))))
        options (mongo/mongo-options)]
    (mongo/connect! address options)))

(defn disconnect-mongo []
  (when (bound? #'mongo/*mongodb-connection*)
    (mongo/disconnect!)))

(defn ensure-mongo-connection []
  (connect-mongo))

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

(defn field-zip-fn
  [slicedef]
  "Given a slice definition, return a function that will compress
  field names."
  (let [fields (slice-columns slicedef)]
    (mzip/compression-fn fields)))

(defn field-unzip-fn
  [slicedef]
  "Given a slice definition, return a function that will compress
  field names."
  (let [fields (slice-columns slicedef)]
    (mzip/decompression-fn fields)))

(defn- strip-id [data]
  (map #(dissoc % :_id) data))

(defrecord QueryResult [total size data])

(defn get-find
  "Given a collection and a Mongo find map, return a QueryResult of the form:
   :total - Total number of documents for the input query irrespective of skip or limit
   :size - Number of documents for the input query after skip and limit are applied
   :data - Seq of maps with the IDs stripped out"
  [database collection find-map]
  (log/info (str "Mongo find: " find-map))

  (with-db (get-db database)
    (with-open [cursor (doto (coll/find collection (:query find-map) (:fields find-map))
                         (.limit (:limit find-map 0))
                         (.skip (:skip find-map 0))
                         (.sort (conv/to-db-object (:sort find-map))))]
      (->QueryResult
       (.count cursor)
       (.size cursor)
       (->> cursor
            (map (fn [x] (conv/from-db-object x true)))
            strip-id)))))

(defn get-aggregation
  "Given a collection and a Mongo aggregation, return a QueryResult of the form:
   :total - Total number of results returned
   :size - Same as :total
   :data - Seq of maps with the IDs stripped out"
  [database collection aggregation]
  (log/info (str "Mongo aggregation: " aggregation))

  (with-db (get-db database)
    (let [data (strip-id (coll/aggregate collection aggregation))
          size (count data)]
      (->QueryResult size size data))))

(defn get-data-table
  "Given retrieved data (a seq of maps) and the columns you want from
that data, return a seq of seqs representing the data in columnar
format."
  [data columns]
  (map (fn [row]
         (map (fn [column]
                (str (row (keyword column)))) columns)) data))

