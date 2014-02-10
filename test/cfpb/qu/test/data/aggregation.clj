(ns cfpb.qu.test.data.aggregation
  (:require [clojure.test :refer :all]
            [cfpb.qu.data.aggregation :refer :all]))


(deftest test-sorting
  (testing "it sorts by first indexed grouped field"
    (let [mr (generate-map-reduce* {:dataset "integration_test"
                                    :from "incomes"
                                    :to "test1"
                                    :group [:state_abbr]
                                    :aggregations {:max_tax_returns ["max" "tax_returns"]}
                                    :slicedef {:indexes ["state_abbr"]}})]

      (is (= (:sort mr)
             (array-map "state_abbr" 1))))


    (let [mr (generate-map-reduce* {:dataset "integration_test"
                                    :from "incomes"
                                    :to "test1"
                                    :group [:county_abbr :state_abbr]
                                    :aggregations {:max_tax_returns ["max" "tax_returns"]}
                                    :slicedef {:indexes ["state_abbr"]}})]

      (is (= (:sort mr)
             (array-map "state_abbr" 1))))))
