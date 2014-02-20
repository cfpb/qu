(ns ^:integration integration.test.aggregation
  (:require [clojure.test :refer :all]
            [cfpb.qu.data.aggregation :refer :all]            
            [cfpb.qu.test-util :refer :all]
            [cfpb.qu.data :as data]
            [cfpb.qu.loader :as loader]))

(use-fixtures :once (mongo-setup-fn "integration_test"))

(deftest test-sorting
  (testing "it sorts by first indexed grouped field"
    (let [metadata (data/get-metadata "integration_test")
          slicedef (get-in metadata [:slices :incomes])]
      
      (let [mr (generate-map-reduce* {:dataset "integration_test"
                                      :from "incomes"
                                      :to "test1"
                                      :group [:state_abbr]
                                      :aggregations {:max_tax_returns ["max" "tax_returns"]}
                                      :slicedef slicedef})]
        (is (= (:sort mr)
               (array-map "4" 1))))

      (let [mr (generate-map-reduce* {:dataset "integration_test"
                                      :from "incomes"
                                      :to "test1"
                                      :group [:state_name :county]
                                      :aggregations {:max_tax_returns ["max" "tax_returns"]}
                                      :slicedef slicedef})]
        (is (= (:sort mr)
               (array-map "a" 1)))))))
