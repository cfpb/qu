(ns qu.test.query.mongo
  (:refer-clojure :exclude [sort])
  (:require [clojure.test :refer :all]
            [qu.test-util :refer :all]
            [qu.query.mongo :refer :all]))

(deftest test-sort
  (testing "it transforms :orderBy into a sorted-map"
    (does-contain (:mongo (sort {:orderBy "name"}))
                  {:sort (sorted-map :name 1)})
    
    (does-contain (:mongo (sort {:orderBy "state, name"}))
                  {:sort (sorted-map :state 1 :name 1)})

    (does-contain (:mongo (sort {:orderBy "state DESC, name"}))
                  {:sort (sorted-map :state -1 :name 1)})))

(deftest test-match
  (testing "it transforms :where into :match"
    (does-contain (:mongo (match {:where "a > 2"}))
                  {:match {:a {:$gt 2}}})))

(deftest test-project
  (testing "it transforms :select into :project"
    (does-contain (:mongo (project {:select "name, city"}))
                  {:project {:name 1, :city 1}}))

  (testing "it transforms aggregations"
    (does-contain (:mongo (project {:select "city, MAX(income)", :group "city"}))
                  {:project {:fields [:city :max_income]
                             :aggregations {:max_income ["max" "income"]}}})))

(deftest test-group
  (testing "it makes a list of all fields to group by"
    (does-contain (:mongo (group {:select "state, county, COUNT()" :group "state, county"}))
                  {:group [:state :county]})))

(deftest test-process
  (let [slicedef {:dimensions ["state_abbr" "county"]
                  :metrics ["tax_returns"]}
        errors (comp :errors process)]         
    (testing "it errors if you use a field in WHERE that does not exist"
      (is (contains? (errors {:where "foo > 0" :slicedef slicedef}) :where)))
    
    (testing "it errors if you try to ORDER BY a field that does not exist"
      (is (contains? (errors {:orderBy "foo" :slicedef slicedef}) :orderBy)))
    
    (testing "it does not error when you ORDER BY an aggregated field"
      (is (not (contains? (errors {:group "state_abbr"
                                   :select "state_abbr, COUNT()"
                                   :orderBy "count"
                                   :slicedef slicedef})
                          :orderBy))))))

;; (run-tests)
