(ns cfpb.qu.query.cache-worker
  (:require
   [monger.core :as mongo :refer [with-db get-db]]
   [monger.collection :as coll]
   [cfpb.qu.query.cache :as qc]
   [clj-time.core :refer [default-time-zone now]]
   [taoensso.timbre :as log])
  (:import
   [com.mongodb MongoException$DuplicateKey]))

(def TODO nil)
(def ^:dynamic *wait-time* 5000)
(def ^:dynamic *collection* "jobs")

(defrecord Worker [cache ping processed kill])

(defn- find-and-claim-unprocessed
  "Find the next unprocessed map-reduce job and claim it.
   Returns job."
  [worker]
  (coll/find-and-modify *collection*
                        {:status "unprocessed"}
                        {"$set" {:status "processing"}}
                        :sort {:created 1}))

(defn- work-job
  "Given a map-reduce job, tells Mongo to perform the job.
   Returns true on success, false on failure."
  [worker job]
  (qc/add-to-cache (:cache worker) (:aggmap job)))

(defn- update-cache
  "Update the query cache to reflect that the map-reduce job is
  complete and ready to be accessed.

  Returns updated record."
  [worker job]
  (let [cache (:cache worker)]
    (coll/update *collection*
                 {:_id (:_id job)}
                 {"$set" {:status "processed"
                          :finished (now)}})
    (qc/touch-cache cache (get-in job [:aggmap :query]))))

(defn- process-next-job
  [worker]
  (when-not (:kill worker)
    (with-db (get-in worker [:cache :database])
      (if-let [job (find-and-claim-unprocessed worker)]
        (and (work-job worker job)
             (update-cache worker job)
             (send *agent* #(update-in % [:processed] inc))
             (log/info "Aggregation" (:_id job) "processed"))
        (Thread/sleep *wait-time*))
      (send-off *agent* process-next-job)))
  (update-in worker [:ping] inc))

(defn create-worker
  "Creates a cache worker. Call `start` with this worker to start it
  processing."
  [cache]
  (map->Worker {:cache cache
                :ping 0
                :processed 0
                :kill false}))

(defn add-to-queue
  [cache aggmap]
  (with-db (:database cache)
    (try
      (coll/insert-and-return *collection* {:_id (:to aggmap)
                                            :status "unprocessed"
                                            :created (now)
                                            :aggmap aggmap})
      (catch MongoException$DuplicateKey e
          (coll/find-map-by-id *collection* (:to aggmap))))))

(defn start
  "Start a cache worker. This will continue to process jobs until stopped.

  Returns an agent with the worker state. Call `stop` on this agent to
  stop the worker."
  [worker]
  (let [worker-agent (agent worker)]
    (send worker-agent #(assoc % :kill false))
    (send-off worker-agent process-next-job)))

(defn stop
  [worker-agent]
  (send worker-agent #(assoc % :kill true)))
