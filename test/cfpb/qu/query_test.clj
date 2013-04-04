(ns cfpb.qu.query-test
  (:require [midje.sweet :refer :all]
            [cfpb.qu.query :refer :all]))

(fact "parse-params returns string clauses"
      (let [params {:$select "age,race"}]
        (:clauses (parse-params params {})) => {:$select "age,race"}))

(facts "about is-aggregation?"
       (fact "returns true if we have a group key"
             (map->Query {:group "state_abbr"}) => is-aggregation?)

       (fact "returns true if we have an AS in :select"
             (map->Query {:select "state_abbr AS state"}) => is-aggregation?
             (map->Query {:select "bass"}) =not=> is-aggregation?))

(facts "about order->mongo"
       (fact "it transforms :order into a sorted-map"
             (order->mongo "name") => (sorted-map "name" 1)
             (order->mongo "state, name") => (sorted-map "state" 1 "name" 1)
             (order->mongo "state DESC, name") => (sorted-map "state" -1 "name" 1)))

(facts "about params->Query"
       (fact "it transforms a comma-separated order into a sorted map"
             (params->Query {:$orderBy "name,state"} {})
             => (contains {:order (sorted-map "name" 1 "state" 1)}))

       (fact "it turns DESC in orderBy into a -1 for order"
             (params->Query {:$orderBy "name desc, state"} {})
             => (contains {:order (sorted-map "name" -1 "state" 1)}))

       (fact "it stores dimensions in the query"
             (params->Query {:state_abbr "AL"}
                            {:dimensions ["state_abbr" "county"]})
             => (contains {:dimensions {:state_abbr "AL"}})))

(facts "about Query->mongo"
       (fact "it parses the where value and adds dimensions"
             (Query->mongo (map->Query {:where "income > 10000"
                                        :dimensions {:state "WY"}
                                        :limit 100 :offset 0}))
             => (contains {:query {:income {"$gt" 10000}
                                   :state "WY"}}))

       (fact "it adds fields iff :select exists"
             (Query->mongo (map->Query {:select "name, state"
                                        :limit 100 :offset 0
                                        }))
             => (contains {:fields {:name 1 :state 1}})

             (Query->mongo (map->Query {:limit 100 :offset 0}))
             =not=> #(contains? % :fields)))

(facts "about Query->aggregation"
       (fact "it creates a chain of filters for Mongo"
             (let [query (map->Query {:select "name, state"
                                      :limit 100
                                      :offset 0
                                      :where "population > 10000000"
                                      :order "name"
                                      :group "state"})]
               (Query->aggregation query) =>
               [{"$project" {:name 1, :state 1, :population 1}}
                {"$match" {:population {"$gt" 10000000}}}
                {"$group" {"_id" "$state"}}
                {"$sort" {"name" 1}}
                {"$skip" 0}
                {"$limit" 100}])))
