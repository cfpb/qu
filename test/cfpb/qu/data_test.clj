(ns cfpb.qu.data-test
  (:require [midje.sweet :refer :all]
            [cfpb.qu.data :refer :all]
            [cfpb.qu.query :refer [is-aggregation? params->Query]]))

(fact "get-data-table converts maps to seqs"
      (let [raw-data [{:name "Pete" :age 36 :city "York"}
                      {:name "Sarah" :age 34 :city "Wallingford"}
                      {:name "Shawn" :age 29 :city "Portland"}]
            data-table [["Pete" "36"]
                        ["Sarah" "34"]
                        ["Shawn" "29"]]]
        (get-data-table raw-data [:name :age]) => data-table))

(facts "about get-data"
       (fact "it calls get-data-mongo-find if is-aggregation? is false"
             (get-data {:table "people"} {}) => [{..mongo-find.. true}]
             (provided
              (params->Query {} anything) => ..query..
              (is-aggregation? ..query..) => false
              (#'cfpb.qu.data/get-data-mongo-find "people" ..query..) =>
              [{..mongo-find.. true}]))

       (fact "it calls get-data-mongo-aggregation if is-aggregation? is true"
             (get-data {:table "people"} {}) => [{..mongo-agg.. true}]
             (provided
              (params->Query {} anything) => ..query..
              (is-aggregation? ..query..) => true
              (#'cfpb.qu.data/get-data-mongo-aggregation "people" ..query..) =>
              [{..mongo-agg.. true}])))

 
