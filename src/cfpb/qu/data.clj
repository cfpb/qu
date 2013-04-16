;; TODO: reverse how this works.
;; data should _only_ talk to the DB with very specific inputs from a query
;; the query namespace should handle getting the results

(ns cfpb.qu.data
  "This namespace contains all our functions for retrieving data from
MongoDB, including creating queries and light manipulation of the data
after retrieval."
  (:require [taoensso.timbre :as log]
            [environ.core :refer [env]]
            [monger
             [core :as mongo :refer [with-db get-db]]
             [query :as q]
             [collection :as coll]
             [conversion :as conv]
             json]))

(defn ensure-mongo-connection []
  (when-not (bound? #'mongo/*mongodb-connection*)
    (let [address (mongo/server-address (env :mongo-host) (env :mongo-port))
          options (mongo/mongo-options)]
      (mongo/connect! address options))))

(defn disconnect-mongo []
  (mongo/disconnect!))

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

(defn- strip-id [data]
  (map #(dissoc % :_id) data))

(defn get-find
  "Given a collection and a Mongo find map, return a map of the form:
   :total - Total number of documents for the input query irrespective of skip or limit
   :size - Number of documents for the input query after skip and limit are applied
   :data - Seq of maps with the IDs stripped out"
  [collection find-map]
  (log/info (str "Mongo find: " find-map))

  (with-open [cursor (doto (coll/find collection (:query find-map) (:fields find-map))
    (.limit (:limit find-map))
    (.skip (:skip find-map))
    (.sort (conv/to-db-object (:sort find-map))))]
    {
      :total (.count cursor)
      :size (.size cursor)
      :data (strip-id (map (fn [x] (conv/from-db-object x true)) cursor))
    }))

(defn get-aggregation
  "Given a collection and a Mongo aggregation, return a map of the form:
   :total - Total number of results returned
   :size - Same as :total
   :data - Seq of maps with the IDs stripped out"
  [collection aggregation]
  (log/info (str "Mongo aggregation: " aggregation))
  (let [data (strip-id (coll/aggregate collection aggregation))
        size (count data)]
    {:total size
     :size size
     :data data}))

(defn get-data-table
  "Given retrieved data (a seq of maps) and the columns you want from
that data, return a seq of seqs representing the data in columnar
format."
  [data columns]
  (map (fn [row]
         (map (fn [column]
                (str (row (keyword column)))) columns)) data))

