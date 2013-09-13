(ns integration.cache-test
  (:require [midje.sweet :refer :all]
            [cfpb.qu.data :as data]
            [cfpb.qu.query :as q]
            [cfpb.qu.cache :as c]
            [cfpb.qu.loader :as loader]
            [monger.core :as mongo]
            [monger.collection :as coll]
            [monger.conversion :as conv]
            [monger.db :as db]))

(data/connect-mongo)

(def db "integration_test")
(def coll "incomes")
(def qmap {:dataset db
           :slice coll
           :select "state_abbr, COUNT()"
           :group "state_abbr"})
(def cache (c/create-query-cache))
(def worker (c/create-worker cache))
(def worker-agent (atom nil))

(defn run-all-jobs []
  (reset! worker-agent (c/start-worker worker))
  (await @worker-agent)
  (swap! worker-agent c/stop-worker))

(with-state-changes [(before :facts (do                                      
                                      (loader/load-dataset db)
                                      (db/drop-db (:database cache))))]
  (let [query (q/prepare (q/make-query qmap))
        agg (q/mongo-aggregation query)]

    (facts "about cache"
         (fact "the default cache uses the query-cache db"
               (str (:database cache)) => "query_cache")

         (fact "you can use other databases"
               (str (:database (c/create-query-cache
                                (mongo/get-db "cashhhh"))))
               => "cashhhh")

         (fact "it can be wiped"
               (do
                 (data/get-aggregation db coll agg)
                 (run-all-jobs)
                 (coll/exists? (:database cache) (:to agg)))
               => true

               (do
                 (c/wipe-cache cache)
                 (coll/exists? (:database cache) (:to agg)))
               => false)

         (fact "it can be cleaned"
               (do
                 (data/get-aggregation db coll agg)
                 (run-all-jobs)
                 (coll/exists? (:database cache) (:to agg)))
               => true

               (do
                 (c/clean-cache cache (constantly [(:to agg)]))
                 (coll/exists? (:database cache) (:to agg)))
               => false))
    
    (facts "about add-to-queue"
           (fact "it adds a document to jobs"
                 (conv/from-db-object (c/add-to-queue cache agg) true)
                 => (contains {:_id (:to agg)
                               :status "unprocessed"})))

    (facts "about worker"
           (fact "it will process jobs"
                 (data/get-aggregation db coll agg)
                 => (contains {:data :computing})
                 
                 (do
                   (run-all-jobs)
                   (data/get-aggregation db coll agg))
                 =not=> (contains {:data :computing})))))

