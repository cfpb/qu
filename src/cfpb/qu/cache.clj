(ns cfpb.qu.cache
  "Functions to create and manipulate a cache for storing the results
of aggregations. This cache will be used to serve up aggregations to
API users without having to go through Mongo's aggregation framework.

The cache uses `clojure.core.cache`'s `CacheProtocol` so that it has a
standard interface. Unlike the caches that come with
`clojure.core.cache`, however, our `QueryCache` is stateful, as it's
backed by MongoDB. That means two instances of the cache created with
the same backing database have access to the same data."
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
            digest)
  (:import
   [com.mongodb
    DBCollection
    MongoException$DuplicateKey
    MapReduceCommand
    MapReduceCommand$OutputType]))

(defn query-to-key
  "Converts a query to a key that can be used to look up the query
  results later. The key must begin with a letter, as it will be used
  as the name of a MongoDB collection, which have to begin with a
  letter."
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
  "Turn a collection + a query into results that would come from that aggregation."
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
                         (.sort (conv/to-db-object sort)))]
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

(defn add-to-cache
  "Add the specified aggregation to the cache by running it through
  MongoDB's map-reduce."
  [cache aggmap]
  (let [dataset (:dataset aggmap)
        map-reduce (agg/generate-map-reduce aggmap)
        map-reduce (update-in map-reduce [:out]
                              (fn [out]
                                {:replace out,
                                 :db (str (:database cache))}))
        database (mongo/get-db (:dataset aggmap))]
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
      (with-db database
        (coll/drop key)
        (coll/insert-batch key result))))
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
  "Create a query cache. If you do not specify a database, the default
one of `query_cache` will be used."
  ([] (create-query-cache (get-db "query_cache")))
  ([database] (->QueryCache database)))

(def ^:dynamic *wait-time* 5000)
(def ^:dynamic *work-collection* "jobs")

(defrecord CacheWorker [cache ping processed kill])

(defn- find-and-claim-unprocessed
  "Find the next unprocessed map-reduce job and claim it.
   Returns job."
  [worker]
  (coll/find-and-modify *work-collection*
                        {:status "unprocessed"}
                        {"$set" {:status "processing"}}
                        :sort {:created 1}))

(defn- work-job
  "Given a map-reduce job, tells Mongo to perform the job.
   Returns true on success, false on failure."
  [worker job]
  (add-to-cache (:cache worker) (:aggmap job)))

(defn- update-cache
  "Update the query cache to reflect that the map-reduce job is
  complete and ready to be accessed.

  Returns updated record."
  [worker job]
  (let [cache (:cache worker)]
    (coll/update *work-collection*
                 {:_id (:_id job)}
                 {"$set" {:status "processed"
                          :finished (now)}})
    (touch-cache cache (get-in job [:aggmap :query]))))

(defn- process-next-job
  [worker]
  (when-not (:kill worker)
    (with-db (get-in worker [:cache :database])
      (if-let [job (find-and-claim-unprocessed worker)]
        (do (log/info "Aggregation" (:_id job) "started")
            (work-job worker job)
            (update-cache worker job)
            (send *agent* #(update-in % [:processed] inc))
            (log/info "Aggregation" (:_id job) "processed"))
        (Thread/sleep *wait-time*))
      (send-off *agent* process-next-job)))
  (update-in worker [:ping] inc))

(defn create-worker
  "Creates a cache worker. Call `start-worker` with this worker to
  start it processing."
  [cache]
  (map->CacheWorker {:cache cache
                     :ping 0
                     :processed 0
                     :kill false}))

(defn add-to-queue
  "Add an aggregation to the queue for caching. Returns the inserted
  record. If the aggregation is already on the queue, then the record
  of the existing job is returned."
  [cache aggmap]
  (with-db (:database cache)
    (try
      (coll/insert-and-return *work-collection* {:_id (:to aggmap)
                                                 :status "unprocessed"
                                                 :created (now)
                                                 :aggmap aggmap})
      (catch MongoException$DuplicateKey e
        (coll/find-map-by-id *work-collection* (:to aggmap))))))

(defn start-worker
  "Start a cache worker. This will continue to process jobs until stopped.

  Returns an agent with the worker state. Call `stop-worker` on this agent to
  stop the worker."
  [worker]
  (let [worker-agent (agent worker
                            :error-mode :continue
                            :error-handler (fn [the-agent exception]
                                             (log/warn "Error with cache worker" @the-agent)
                                             (log/warn (.getMessage exception))
                                             (log/warn "=== END EXCEPTION ===")
                                             (send-off the-agent process-next-job)))]
    (send worker-agent #(assoc % :kill false))
    (send-off worker-agent process-next-job)))

(defn stop-worker
  "Stop a cache worker. This function does not take a worker: it takes
  the agent returned from `start-worker`."
  [worker-agent]
  (send worker-agent #(assoc % :kill true)))
