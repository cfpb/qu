(ns qu.test.query
  (:require [clojure.test :refer :all]
            [qu.test-util :refer :all]
            [qu.query :refer :all]
            [qu.query.mongo :as mongo]))

(def metadata
  {:slices {:county_taxes {:dimensions ["state" "county"]
                           :metrics ["tax_returns" "population"]}}})

(defn make-test-query
  [q]
  (merge {:metadata metadata
          :slicedef (get-in metadata [:slices :county_taxes])
          :limit "0" :offset "0"} q))

(def query (make-test-query {}))

(deftest test-parse-params
  (testing "it pulls out clauses"
    (let [params {:$select "age,race" :$foo "1"}]
      (is (= (parse-params params) {:$select "age,race"})))))

(deftest test-is-aggregation?
  (testing "returns true if we have a group key"
    (is (is-aggregation? {:group "state"})))

  (testing "returns false if we don't have a group key"
    (is (not (is-aggregation? {})))))

(deftest test-mongo-find
  (testing "it populates fields if :select exists"
    (does-contain (mongo-find (mongo/process (make-test-query {:select "county, state"})))
                  {:fields {:county 1 :state 1}}))

  (testing "it returns empty fields if :select does not exist"
    (does-contain (mongo-find query) {:fields {}}))

  (testing "it returns empty sort if :sort does not exist"
    (does-contain (mongo-find query) {:sort {}})))

(deftest test-mongo-aggregation
  (testing "it creates a map-reduce query for Mongo"
    (let [query (mongo/process (make-test-query {:select "state, SUM(population)"
                                                 :limit 100
                                                 :offset 0
                                                 :where "land_area > 1000000"
                                                 :orderBy "state"
                                                 :group "state"}))]
      (does-contain (mongo-aggregation query)
                    {:group [:state]
                     :aggregations {:sum_population ["sum" "population"]}
                     :sort {:state 1}
                     :limit 100
                     :offset 0}))))

;; (run-tests)
