(ns cfpb.qu.test.data
  (:require [clojure.test :refer :all]
            [cfpb.qu.data :refer :all]))

(deftest test-get-data-table
  (let [raw-data [{:name "Pete" :age 36 :city "York"}
                  {:name "Sarah" :age 34 :city "Wallingford"}
                  {:name "Shawn" :age 29 :city "Portland"}]
        data-table [["Pete" "36"]
                    ["Sarah" "34"]
                    ["Shawn" "29"]]]
    (is (= (get-data-table raw-data [:name :age])
           data-table))))
