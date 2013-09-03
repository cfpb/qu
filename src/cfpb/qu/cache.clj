(ns cfpb.qu.cache
  (:require [taoensso.timbre :as log]
            [cfpb.qu.util :refer :all]
            [cfpb.qu.data.result :refer [->DataResult]]
            [cfpb.qu.data.aggregation :as agg]
            [clojure.string :as str]
            [clojure.core.cache :as cache :refer [defcache]]
            [clj-time.core :refer [now]]            
            [lonocloud.synthread :as ->]            
            [monger
             [core :as mongo :refer [with-db get-db]]
             [query :as q]
             [collection :as coll]
             [conversion :as conv]
             joda-time
             json]
            digest))

(defn query-to-key
  "Converts a query to a key that can be used to look up the query
  results later."
  [query]
  (let [squeeze #(str/replace % " " "")
        database (:database query)
        slice (:slice query)
        select (:select query)
        group (:group query)
        where (:where query)
        sqlish (-> ["SELECT" (squeeze select) "FROM" (str database "." slice)]
                   (->/when where
                     (conj "WHERE" (str/trim where)))
                   (->/when group
                     (conj "GROUP BY" (squeeze group)))
                   (->/as sqlish
                          (do (str/join " " sqlish))))]
    (str "q" (digest/md5 sqlish))))

(defn- extract-result
  [collection query]
  (let [limit (->int (:limit query) 0)
        offset (->int (:offset query) 0)
        values (set (keys (get-in query [:mongo :project :aggregations])))
        sort (into {} (map (fn [[field order]]
                             (if (contains? values field)
                               [(str "value." (name field)) order]
                               [(str "_id." (name field)) order]))
                           (get-in query [:mongo :sort] {})))
        fields (get-in query [:mongo :project :fields])
        flatten-row (fn [row]
                      (select-keys (merge (:_id row) (:value row)) fields))]
    (with-open [cursor (doto (coll/find collection {})
                         (.limit limit)
                         (.skip offset)
                         (.sort (conv/to-db-object sort))
                         )]
    (->DataResult
     (.count cursor)
     (.size cursor)
     (map (fn [x] (-> x
                      (conv/from-db-object true)                           
                      (flatten-row))) cursor)))))

(defn- get-collection
  ([database query]
     (get-collection database query nil))
  ([database query not-found]
     (with-db database
       (let [collection (query-to-key query)]
         (if (coll/exists? collection)   
           (extract-result collection query)
           not-found)))))

(defn- insert-collection
  [database collection data]
  (with-db database
    (coll/drop collection)
    (coll/insert-batch collection data)))

(defn add-to-cache
  "Add the specified aggregation to the cache."
  [cache {:keys [dataset query] :as aggmap}]
  (let [map-reduce (agg/generate-map-reduce aggmap)
        map-reduce (update-in map-reduce [:out]
                              (fn [out]
                                {:replace out,
                                 :db (str (:database cache))}))
        database (mongo/get-db dataset)]
    (with-db database
      (mongo/command map-reduce))))

(defn touch-cache
  "Sets the created value for a query to now."
  [cache query]
  (let [key (query-to-key query)]
    (with-db (:database cache)
      (coll/update "metadata"
                   {:_id key}
                   {"$set" {:created (now)}}
                   :upsert true))))

(defrecord QueryCache [database]
  cache/CacheProtocol
  (lookup [cache query]
    (let [key (query-to-key query)]
      (get-collection database query)))
  (lookup [cache query not-found]
    (let [key (query-to-key query)]
      (get-collection database query not-found)))
  (has? [cache query]
    (with-db database
      (let [key (query-to-key query)]
        (coll/exists? key))))
  (hit [cache query]
    (let [key (query-to-key query)]
      (with-db database
        (coll/update "metadata"
                     {:_id key}
                     {"$set" {:last_viewed (now)}
                      "$inc" {:view_count 1}}
                     :upsert true)))
    cache)
  (miss [cache query result]
    (let [key (query-to-key query)]
      (insert-collection database key result)))
  (evict [cache query]
    (with-db database
      (let [key (query-to-key query)]
        (coll/drop key))))
  (seed [cache queries]
    (with-db database
      (doseq [[query result] queries]
        (let [key (query-to-key query)]
          (coll/drop key)
          (coll/insert-batch key result))))))

(defn create-query-cache
  "Create a query cache."
  ([] (create-query-cache (get-db "query_cache")))
  ([database] (QueryCache. database)))
