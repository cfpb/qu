(ns integration.cache-test
  (:require [midje.sweet :refer :all]
            [cfpb.qu.data :as data]
            [cfpb.qu.query :as q]
            [cfpb.qu.cache :as c]
            [cfpb.qu.loader :as loader]
            [monger.core :as mongo]
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

(with-state-changes [(before :facts (do                                      
                                      (loader/load-dataset db)
                                      (db/drop-db (:database cache))
                                      #_(reset! worker-agent (c/start-worker worker))))
                     (after :facts (do true
                                       #_(swap! worker-agent c/stop-worker)))]

  (facts "about cache"
         (fact "the default cache uses the query-cache db"
               (str (:database cache)) => "query_cache")

         (fact "you can use other databases"
               (str (:database (c/create-query-cache
                                (mongo/get-db "cashhhh"))))
               => "cashhhh"))

  (let [query (q/prepare (q/make-query qmap))
        agg (q/mongo-aggregation query)]
    (facts "about add-to-queue"
           (fact "it adds a document to jobs"
                 (conv/from-db-object (c/add-to-queue cache agg) true)
                 => (contains {:aggmap agg
                               :status "unprocessed"})))))

