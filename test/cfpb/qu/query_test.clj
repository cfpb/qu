(ns cfpb.qu.query-test
  (:require [midje.sweet :refer :all]
            [cfpb.qu.query :refer :all]))

(facts "about params->Query"
       (fact "it transforms a comma-separated select into a vector"
             (params->Query {:dimensions {}
                                      :clauses {:$select "name,state"}})
             => (contains {:select ["name" "state"]}))

       (fact "it transforms a comma-separated order into a sorted map"
             (params->Query {:dimensions {}
                                      :clauses {:$orderBy "name,state"}})
             => (contains {:order (sorted-map "name" 1 "state" 1)}))

       (fact "it turns DESC in orderBy into a -1 for order"
             (params->Query {:dimensions {}
                                      :clauses {:$orderBy "name desc, state"}})
             => (contains {:order (sorted-map "name" -1 "state" 1)}))

       (fact "it stores dimensions in the query"
             (params->Query {:dimensions {:state_abbr "AL"}
                                      :clauses {}})
             => (contains {:dimensions {:state_abbr "AL"}})))

(facts "about Query->mongo"
       (fact "it parses the where value and adds dimensions"
             (Query->mongo (map->Query {:where "income > 10000"
                                        :dimensions {:state "WY"}
                                        :limit 100 :offset 0 :order {}}))
             => (contains {:query {:income {"$gt" 10000}
                                   :state "WY"}}))

       (fact "it adds fields iff :select exists"
             (Query->mongo (map->Query {:select ["name" "state"]
                                        :limit 100 :offset 0 :order {} :where ""
                                        }))
             => (contains {:fields ["name" "state"]})

             (Query->mongo (map->Query {:limit 100 :offset 0 :order {} :where ""}))
             =not=> #(contains? % :fields)))
       
