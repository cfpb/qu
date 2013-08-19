(ns cfpb.qu.data
  "This namespace contains all our functions for retrieving data from
MongoDB, including creating queries and light manipulation of the data
after retrieval."
  (:require [taoensso.timbre :as log]
            [clojure.string :as str]
            [clojure.walk :refer [postwalk]]
            [cfpb.qu.util :refer :all]
            [cfpb.qu.env :refer [env]]
            [clj-statsd :as sd]
            [monger
             [core :as mongo :refer [with-db get-db]]
             [query :as q]
             [collection :as coll]
             [conversion :as conv]
             [key-compression :as mzip]
             joda-time
             json]))

(defn- authenticate-mongo
  [auth]
  (doseq [[db [username password]] auth]
    (mongo/authenticate (mongo/get-db (name db))
                        username
                        (.toCharArray password))))

(defn connect-mongo
  []
  (let [uri (env :mongo-uri)
        hosts (env :mongo-hosts)
        host (env :mongo-host)
        port (->int (env :mongo-port))
        options (apply-kw mongo/mongo-options (env :mongo-options {}))
        auth (env :mongo-auth)
        connection 
        (cond
         uri (try (mongo/connect-via-uri! uri)
                  (catch Exception e
                    (log/error "The Mongo URI specified is invalid.")))
         hosts (let [addresses (map #(apply mongo/server-address %) hosts)]
                 (mongo/connect! addresses options))
         :else (mongo/connect! (mongo/server-address host port) options))]
    (if (map? auth)
      (authenticate-mongo auth))
    connection))

(defn disconnect-mongo
  []
  (when (bound? #'mongo/*mongodb-connection*)
    (mongo/disconnect!)))

(defn ensure-mongo-connection
  []
  (when-not (bound? #'mongo/*mongodb-connection*)
    (connect-mongo)))

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
  "Given a slice definition, return a function that will compress
  field names."
  ([dataset slice]
     (let [metadata (get-metadata dataset)
           slicedef (get-in metadata [:slices (keyword slice)])]
       (field-zip-fn slicedef)))
  ([slicedef]
    (let [fields (slice-columns slicedef)]
      (sd/with-timing "qu.queries.fields.zip"
       (mzip/compression-fn fields)))))

(defn field-unzip-fn
  "Given a slice definition, return a function that will compress
  field names."
  ([dataset slice]
     (let [metadata (get-metadata dataset)
           slicedef (get-in metadata [:slices (keyword slice)])]
       (field-unzip-fn slicedef)))
  ([slicedef]
     (let [fields (slice-columns slicedef)]
       (sd/with-timing "qu.queries.fields.unzip"
        (mzip/decompression-fn fields)))))

(defn- strip-id [data]
  (map #(dissoc % :_id) data))

(defn- text? [text]
  (or (string? text)
      (symbol? text)))

(defn compress-where
  [where zipfn]
  (let [f (fn [[k v]] (if (keyword? k) [(zipfn k) v] [k v]))]
    ;; only apply to maps
    (postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) where)))

(defn compress-group
  [group zipfn]
  (let [f (fn [[k v]] 
            (if (and (string? v)
                     (.startsWith v "$"))
              [k (str+ "$" (zipfn (.substring v 1)))]
              [k v]))]
    (postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) group)))

(defn compress-fields
  [fields zipfn]
  (convert-keys fields zipfn))

(defrecord QueryResult [total size data])

(defn compress-find
  [database collection find-map]
  (let [zipfn (field-zip-fn database collection)]
    (-> find-map
        (update-in [:query] compress-where zipfn)
        (update-in [:fields] convert-keys zipfn)
        (update-in [:sort] convert-keys zipfn))))

(defn get-find
  "Given a collection and a Mongo find map, return a QueryResult of the form:
   :total - Total number of documents for the input query irrespective of skip or limit
   :size - Number of documents for the input query after skip and limit are applied
   :data - Seq of maps with the IDs stripped out"
  [database collection find-map]
  (sd/with-timing "qu.queries.find"
    (log/info (str "Mongo find: " find-map))
    (let [find-map (compress-find database collection find-map)
          unzipfn (field-unzip-fn database collection)]
      (log/info "Post-compress Mongo find: " find-map)
      (with-db (get-db database)
        (with-open [cursor (doto (coll/find collection (:query find-map) (:fields find-map))
                             (.limit (:limit find-map 0))
                             (.skip (:skip find-map 0))
                             (.sort (conv/to-db-object (:sort find-map))))]
          (->QueryResult
           (.count cursor)
           (.size cursor)
           (->> cursor
                (map (fn [x] (-> x
                                 (conv/from-db-object true)
                                 (convert-keys unzipfn))))
                strip-id)))))))

(defn compress-aggregation
  [database collection aggregation]

  (let [zipfn (field-zip-fn database collection)
        compress (fn [operation op-type compress-fn]
                   (update-in operation [op-type] compress-fn zipfn))]
    (loop [operations aggregation,
           aggregation []
           projected false]
      (cond
       projected (concat aggregation operations)
       (empty? operations) aggregation

       :else
       (let [operation (first operations)]
         (case (keys operation)
           ["$match"] (recur (rest operations)
                             (conj aggregation (compress operation "$match" compress-where))
                             projected)            
           ["$group"] (recur (rest operations)
                             (conj aggregation (compress operation "$group" compress-group))
                             projected)
           ["$project"] (recur (rest operations)
                               (conj aggregation operation)
                               true)
           (recur (rest operations)
                  (conj aggregation operation)
                  projected)))))))

(defn get-aggregation
  "Given a collection and a Mongo aggregation, return a QueryResult of the form:
   :total - Total number of results returned
   :size - Same as :total
   :data - Seq of maps with the IDs stripped out

  After adding the compression processing, $match MUST come before $group."
  [database collection aggregation]
  (sd/with-timing "qu.queries.aggregation"
    (log/info (str "Mongo aggregation: " aggregation))

    (let [aggregation (compress-aggregation database collection aggregation)]
      (log/info (str "Post-compress Mongo aggregation: " (into [] aggregation)))
      (with-db (get-db database)
        (let [data (strip-id (coll/aggregate collection aggregation))
              size (count data)]
          (->QueryResult size size data))))))

(defn get-data-table
  "Given retrieved data (a seq of maps) and the columns you want from
that data, return a seq of seqs representing the data in columnar
format."
  [data columns]
  (map (fn [row]
         (map (fn [column]
                (str (row (keyword column)))) columns)) data))

