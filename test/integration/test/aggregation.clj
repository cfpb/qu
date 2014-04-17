(ns ^:integration integration.test.aggregation
  (:require [clojure.test :refer :all]
            [qu.data.aggregation :refer :all]            
            [qu.test-util :refer :all]
            [qu.data :as data]
            [qu.data.compression :refer [compress-where field-zip-fn field-unzip-fn]]
            [qu.loader :as loader]))

(use-fixtures :once (mongo-setup-fn "integration_test"))

(deftest test-agg-query
  (testing "it generates the appropriate agg-query"
    (let [metadata (data/get-metadata "integration_test")
          slicedef (get-in metadata [:slices :incomes])]
      (let [agg-query (generate-agg-query {:dataset "integration_test"
                                           :from "incomes"
                                           :to "test1"
                                           :group [:state_abbr]
                                           :aggregations {:max_tax_returns ["max" "tax_returns"]}
                                           :slicedef slicedef})
            z (comp name (field-zip-fn slicedef))]
        (is (= agg-query
               [{"$group" {:_id {:state_abbr (str "$" (z "state_abbr"))}
                           :max_tax_returns {"$max" (str "$" (z "tax_returns"))}}}
                {"$project" {:_id 0
                             :state_abbr "$_id.state_abbr"
                             :max_tax_returns 1}}
                {"$out" "test1"}]))))))

;; (run-tests)
