(ns cfpb.qu.test.data
  (:require [midje.sweet :refer :all]
            [cfpb.qu.data :refer :all]))

(fact "parse-params returns string clauses"
      (let [slice-def {}
            params {:$select "age,race"}]
        (:clauses (parse-params slice-def params)) => {:$select "age,race"}))

(fact "get-data-table converts maps to seqs"
      (let [raw-data [{:name "Pete" :age 36 :city "York"}
                      {:name "Sarah" :age 34 :city "Wallingford"}
                      {:name "Shawn" :age 29 :city "Portland"}]
            data-table [["Pete" "36"]
                        ["Sarah" "34"]
                        ["Shawn" "29"]]]
        (get-data-table raw-data [:name :age]) => data-table))
