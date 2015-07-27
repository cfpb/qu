(ns qu.cache
  "Functions to create and manipulate a cache for storing the results
of aggregations. This cache will be used to serve up aggregations to
API users without having to go through Mongo's aggregation framework.

The cache uses `clojure.core.cache`'s `CacheProtocol` so that it has a
standard interface. Unlike the caches that come with
`clojure.core.cache`, however, our `QueryCache` is stateful, as it's
backed by MongoDB. That means two instances of the cache created with
the same backing database have access to the same data."
  (:require [clj-time.core :refer [now]]
            [clojure.core.cache :as cache]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [digest :refer [md5]]
            [lonocloud.synthread :as ->]
            [monger.collection :as coll]
            [monger.conversion :as conv]
            [monger.core :as mongo :refer [get-db with-db]]
            [qu.data.aggregation :as agg]
            [qu.data.result :refer [->DataResult]]
            [qu.metrics :as metrics]
            [qu.util :refer :all]
            [taoensso.timbre :as log])
  (:import (com.mongodb MongoException$DuplicateKey)))

;; Needed to interact with joda-time and Cheshire.
(require 'monger.joda-time)
(require 'monger.json)

(def ^:dynamic *wait-time* 5000)
(def ^:dynamic *work-collection* "jobs")

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
    (str "q" (md5 sqlish))))

(defn- extract-result
  "Turn a collection + a query into results that would come from that aggregation."
  [collection query]
  (let [limit (->int (:limit query) 0)
        offset (->int (:offset query) 0)
        sort (get-in query [:mongo :sort] {})
        integerize #(if (and (float? %) (is-int? %)) (int %) %)
        integerize-row (fn [row]
                         (into {} (map (fn [[k v]] (vector k (integerize v))) row)))]
    (with-open [cursor (doto (coll/find collection {})
                         (.limit limit)
                         (.skip offset)
                         (.sort (conv/to-db-object sort)))]
      (->DataResult
       (.count cursor)
       (.size cursor)
       (map (fn [x] (-> x
                        (conv/from-db-object true)
                        (integerize-row)
                        (dissoc :_id))) cursor)))))

(defn- get-collection
  ([database query]
     (get-collection database query nil))
  ([database query not-found]
     (with-db database
       (let [collection (query-to-key query)]
         (if (coll/exists? collection)
           (do (log/info "FOUND Aggregation - ID:" collection)
            (metrics/increment "cache.hit.count")
             (extract-result collection query))
           (do (metrics/increment "cache.wait.count")
               not-found))))))

(defn add-to-cache
  "Add the specified aggregation to the cache by running it through
  MongoDB's map-reduce."
  [cache aggmap]
  (let [dataset (:dataset aggmap)
        agg-query (agg/generate-agg-query aggmap)
        source-database (mongo/get-db (:dataset aggmap))
        to-collection (:to aggmap)]
    (log/info "Running aggregation query:" source-database agg-query)
    (with-db source-database
      (let [raw-result (mongo/command (sorted-map :aggregate (:from aggmap) :pipeline agg-query :allowDiskUse true))]
        (log/info "Raw Result for aggregation" (:to aggmap) raw-result)
        raw-result))))

(defn touch-cache
  "Sets the created value for a query to now."
  [cache query]
  (let [key (query-to-key query)
        dataset (:dataset query)]
    (with-db (:database cache)
      (coll/update "metadata"
                   {:_id key}
                   {"$set" {:created (now)
                            :dataset dataset}}
                   :upsert true))))

(defn clean-cache
  "Clean out the cache according to rules defined by the clean-fn or another fun.
   Fun should take a cache and emit a seq of ids to clear."
  ([cache] (clean-cache cache (:clean-fn cache)))
  ([cache fun]
     (mongo/with-db (:database cache)
       (doseq [key (fun cache)]
         (let [metadata (coll/find-one-as-map "metadata" {:_id key})
               dataset (:dataset metadata)
               db (when dataset (mongo/get-db dataset))]
           (when db
             (coll/remove-by-id *work-collection* key)         
             (coll/drop db key)
             (coll/remove-by-id "metadata" key)))))))

(defn wipe-cache
  "Wipe out the entire cache, including the list of jobs."
  [cache]
  (let [db (:database cache)
        colls (coll/find-maps db "metadata" {} ["_id" "dataset"])]
    (doseq [{db :dataset c :_id} colls]      
      (coll/drop (mongo/get-db db) c))
    (coll/drop db "jobs")
    (coll/drop db "metadata")))

(defrecord QueryCache [database clean-fn]
  cache/CacheProtocol
  (lookup [cache query]
    (let [key (query-to-key query)
          database (get-db (:dataset query))]
      (get-collection database query)))
  (lookup [cache query not-found]
    (let [key (query-to-key query)
          database (get-db (:dataset query))]
      (get-collection database query not-found)))
  (has? [cache query]
    (with-db (get-db (:dataset query))
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
    (let [key (query-to-key query)
          database (get-db (:dataset query))]
      (with-db database
        (coll/drop key)
        (coll/insert-batch key result))))
  (evict [cache query]
    (let [key (query-to-key query)
          database (get-db (:dataset query))]
      (with-db database
        (coll/drop key))))
  (seed [cache queries]
    (doseq [[query result] queries]
      (let [key (query-to-key query)
            database (get-db (:dataset query))]
        (with-db database
          (coll/drop key)
          (coll/insert-batch key result))))))

(defn create-query-cache
  "Create a query cache. If you do not specify a database, the default
one of `query_cache` will be used."
  ([] (->QueryCache (get-db "query_cache") (constantly [])))
  ([database] (->QueryCache (get-db database) (constantly [])))
  ([database clean-fn] (->QueryCache (get-db database) clean-fn)))

(defrecord CacheWorker [cache ping processed kill])

(defn- find-and-claim-unprocessed
  "Find the next unprocessed map-reduce job and claim it.
   Returns job."
  []
  (coll/find-and-modify *work-collection*
                        {:status "unprocessed"}
                        {"$set" {:status "processing"
                                 :started (now)}}
                        :sort {:created 1}))

(defn- reset-job
  "Reset a job being processed back to unprocessed."
  [job-id]
  (coll/find-and-modify *work-collection*
                        {:_id job-id :status "processing"}
                        {"$set" {:status "unprocessed"
                                 :created (now)}
                         "$inc" {:reset_count 1}}))

(defn- work-job
  "Given a map-reduce job, tells Mongo to perform the job.
   Returns true on success, false on failure."
  [worker job]
  (add-to-cache (:cache worker) (edn/read-string (:aggmap job))))

(defn- update-cache
  "Update the query cache to reflect that the map-reduce job is
  complete and ready to be accessed.

  Returns updated record."
  [worker job]
  (let [cache (:cache worker)
        aggmap (edn/read-string (:aggmap job))]
    (coll/update *work-collection*
                 {:_id (:_id job)}
                 {"$set" {:status "processed"
                          :finished (now)}})
    (touch-cache cache (:query aggmap))))

(defn- process-next-job
  [worker]
  (when-not (:kill worker)
    (with-db (get-in worker [:cache :database])
      (if-let [job (find-and-claim-unprocessed)]
        (let [job-id (:_id job)]
          (log/info "Aggregation" job-id "started")
          (send *agent* #(assoc-in % [:last-job-id] job-id))
          (work-job worker job)
          (update-cache worker job)
          (send *agent* #(update-in % [:processed] inc))
          (log/info "Aggregation" job-id "processed"))
        (do
          (clean-cache (:cache worker))
          (Thread/sleep *wait-time*)))
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
      (metrics/increment "cache.queue.count")
      (coll/insert-and-return *work-collection* {:_id (:to aggmap)
                                                 :status "unprocessed"
                                                 :created (now)
                                                 :aggmap (pr-str aggmap)})
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
                                             (let [job-id (:last-job-id @the-agent)]
                                               (log/error "Error with cache worker" @the-agent)
                                               (log/error "Last job ID" job-id)
                                               (log/error exception)
                                               (log/error "=== END EXCEPTION ===")
                                               (reset-job job-id)
                                               (send-off the-agent process-next-job))))]
    (send worker-agent #(assoc % :kill false))
    (send-off worker-agent process-next-job)))

(defn stop-worker
  "Stop a cache worker. This function does not take a worker: it takes
  the agent returned from `start-worker`."
  [worker-agent]
  (send worker-agent #(assoc % :kill true)))
