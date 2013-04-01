(ns cfpb.qu.data
  "This namespace contains all our functions for retrieving data from
MongoDB, including creating queries and light manipulation of the data
after retrieval."
  (:require [taoensso.timbre :as log]
            [environ.core :refer [env]]
            [cfpb.qu.query :refer [params->Query Query->mongo]]
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

(defn get-data
  "Given the definition of a slice (from the dataset's metadata) and a
  map with the queried dimensions and other clauses for the request,
  return the queried data from the slice.

  $where and $group are currently not supported clauses, although
  their presence will cause no errors."
  [slice parsed-params]
  (let [table (:table slice)
        query (params->Query parsed-params)]
    (map #(dissoc % :_id)
         (q/with-collection table
           (merge (Query->mongo query))))))

(defn get-data-table
  "Given retrieved data (a seq of maps) and the columns you want from
that data, return a seq of seqs representing the data in columnar
format."
  [data columns]
  (map (fn [row]
         (map (fn [column]
                (str (row (keyword column)))) columns)) data))

