(ns qu.test.data
  (:require [clojure.test :refer :all]
            [qu.data :refer :all]
            [qu.query :refer [is-aggregation? params->Query]]))

(deftest test-get-data-table
  (testing "it converts maps to seqs"
    (let [raw-data [{:name "Pete" :age 36 :city "York"}
                    {:name "Sarah" :age 34 :city "Wallingford"}
                    {:name "Shawn" :age 29 :city "Portland"}]
          data-table [["Pete" 36]
                      ["Sarah" 34]
                      ["Shawn" 29]]]
      (is (= (get-data-table raw-data [:name :age])
             data-table)))))

;; (run-tests)
