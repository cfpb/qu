(ns cfpb.qu.query-test
  (:require [midje.sweet :refer :all]
            [cfpb.qu.query :refer :all]))

(fact "parse-params returns string clauses"
      (let [params {:$select "age,race"}]
        (:clauses (parse-params params {})) => {:$select "age,race"}))

(facts "about is-aggregation?"
       (fact "returns true if we have a group key"
             {:group "state_abbr"} => is-aggregation?)

       (fact "returns true if we have an AS in :select"
             {:select "state_abbr AS state"} => is-aggregation?
             {:select "bass"} =not=> is-aggregation?))

(facts "about order->mongo"
       (fact "it transforms :order into a sorted-map"
             (order->mongo "name") => (sorted-map "name" 1)
             (order->mongo "state, name") => (sorted-map "state" 1 "name" 1)
             (order->mongo "state DESC, name") => (sorted-map "state" -1 "name" 1)))

(facts "about params->Query"
       (fact "it stores dimensions in the query"
             (params->Query {:state_abbr "AL"}
                            {:dimensions ["state_abbr" "county"]})
             => (contains {:dimensions {:state_abbr "AL"}})))

(facts "about Query->mongo"
       (fact "it transforms a comma-separated order into a sorted map"
             (Query->mongo {:order "name, state"})
             => (contains {:sort (sorted-map "name" 1 "state" 1)}))

       (fact "it turns DESC in orderBy into a -1 for order"
             (Query->mongo {:order "name desc, state"})
             => (contains {:sort (sorted-map "name" -1 "state" 1)}))

       (fact "it parses the where value and adds dimensions"
             (Query->mongo {:where "income > 10000"
                            :dimensions {:state "WY"}})
             => (contains {:query {:income {"$gt" 10000}
                                   :state "WY"}}))

       (fact "it adds fields iff :select exists"
             (Query->mongo {:select "name, state"})
             => (contains {:fields {:name 1 :state 1}})

             (Query->mongo {})
             =not=> #(contains? % :fields)))

(facts "about Query->aggregation"
       (fact "it creates a chain of filters for Mongo"
             (let [query {:select "name, state"
                          :limit 100
                          :offset 0
                          :where "population > 10000000"
                          :order "name"
                          :group "state"}]
               (Query->aggregation query) =>
               [{"$project" {:name 1, :state 1, :population 1}}
                {"$match" {:population {"$gt" 10000000}}}
                {"$group" {"_id" "$state"}}
                {"$sort" {"name" 1}}
                {"$skip" 0}
                {"$limit" 100}])))
