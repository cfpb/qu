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
  "Given a collection and a Mongo find map, return the data with the
  IDs stripped out."
  [collection find-map]
  (log/info (str "Mongo find: " find-map))
  (strip-id (q/with-collection collection
              (merge find-map))))

(defn get-aggregation
  "Given a collection and a Mongo aggregation, return the data with
  the IDs stripped out."
  [collection aggregation]
  (log/info (str "Mongo aggregation: " aggregation))
  (strip-id (coll/aggregate collection aggregation)))

(defn get-data-table
  "Given retrieved data (a seq of maps) and the columns you want from
that data, return a seq of seqs representing the data in columnar
format."
  [data columns]
  (map (fn [row]
         (map (fn [column]
                (str (row (keyword column)))) columns)) data))

