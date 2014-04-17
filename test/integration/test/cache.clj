(ns ^:integration integration.test.cache
  (:require [clojure.test :refer :all]
            [clojure.core.cache :as cache]
            [qu.test-util :refer :all]
            [qu.data :as data]
            [qu.query :as q]
            [qu.cache :as c]
            [qu.loader :as loader]
            [monger.core :as mongo]
            [monger.collection :as coll]
            [monger.conversion :as conv]
            [monger.db :as db]
            [qu.main :as main]            
            [qu.app :as app]
            [qu.app.mongo :refer [new-mongo]]
            [com.stuartsierra.component :as component]))

(def db "integration_test")
(def coll "incomes")
(def qmap {:dataset db
           :slice coll
           :select "state_abbr, COUNT()"
           :group "state_abbr"})
(def ^:dynamic cache nil)
(def ^:dynamic worker nil)
(def ^:dynamic query nil)
(def ^:dynamic agg nil)
(def ^:dynamic dbo nil)
(def worker-agent (atom nil))

(defn run-all-jobs [worker]
  (reset! worker-agent (c/start-worker worker))
  (await @worker-agent)
  (swap! worker-agent c/stop-worker))

(defn mongo-setup
  [test]
  (let [mongo (new-mongo (main/default-mongo-options))]
    (component/start mongo)
    (loader/load-dataset db)
    (binding [cache (c/create-query-cache)
              dbo (mongo/get-db db)]
      (db/drop-db (:database cache))
      (binding [query (q/prepare (q/make-query qmap))]
        (binding [worker (c/create-worker cache)
                  agg (q/mongo-aggregation query)]
          (test))))
    (component/stop mongo)))

(use-fixtures :once mongo-setup)

(deftest ^:integration test-cache
  (testing "the default cache uses the query-cache db"
    (does= (str (:database cache)) "query_cache"))

  (testing "you can use other databases"
    (does= (str (:database (c/create-query-cache "cashhhh")))
           "cashhhh"))
  
  (testing "it can be wiped"
    (data/get-aggregation db coll agg)
    (run-all-jobs worker)
    (is (coll/exists? dbo (:to agg)))

    (c/wipe-cache cache)
    (is (not (coll/exists? dbo (:to agg))))))

(deftest ^:integration test-cleaning-cache
  (testing "it can be cleaned"
    (data/get-aggregation db coll agg)
    (run-all-jobs worker)
    (is (coll/exists? dbo (:to agg)))

    (c/clean-cache cache (constantly [(:to agg)]))
    (is (not (coll/exists? dbo (:to agg)))))

  (testing "by default, it cleans nothing"
    (data/get-aggregation db coll agg)
    (run-all-jobs worker)
    (is (coll/exists? dbo (:to agg)))

    (c/clean-cache cache)
    (is (coll/exists? dbo (:to agg))))

  (testing "it runs cleaning operations as part of the worker cycle"
    (let [cleanups (atom 0)
          cache (c/create-query-cache "query_cache" (fn [_] (swap! cleanups inc) []))
          worker (c/create-worker cache)]                 
      (run-all-jobs worker)
      (is (>= @cleanups 1)))))

(deftest ^:integration test-add-to-queue
  (testing "it adds a document to jobs"
    (c/clean-cache cache (constantly [(:to agg)]))    
    (does-contain (conv/from-db-object (c/add-to-queue cache agg) true)
                  {:_id (:to agg) :status "unprocessed"})))

(deftest ^:integration test-add-to-cache
  (testing "it puts the aggregation results into the cache"
    (c/add-to-cache cache agg)
    (is (coll/exists? dbo (:to agg)))))

(deftest ^:integration test-lookup
  (testing "it returns an empty result if not in the cache"
    (c/clean-cache cache (constantly [(:to agg)]))
    (is (nil? (cache/lookup cache query))))
  
  (testing "it returns the cached results if they exist"
    (c/add-to-cache cache agg)
    (let [result (cache/lookup cache query)]
      (is (not (nil? result)))
      (is (= 4 (:total result))))))

(deftest ^:integration test-worker
  (testing "it will process jobs"
    (c/clean-cache cache (constantly [(:to agg)]))
    (is (:computing (data/get-aggregation db coll agg)))
             
    (run-all-jobs worker)
    (is (not (:computing (data/get-aggregation db coll agg))))))

;; (run-tests)
