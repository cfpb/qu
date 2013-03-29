(ns cfpb.qu.query-test
  (:require [midje.sweet :refer :all]
            [cfpb.qu.query :refer :all]))

(facts "about make-query"
       (fact "it has a default limit of 100"
             (:limit (make-query)) => 100)
       
       (fact "it has a default offset of 0"
             (:offset (make-query)) => 0))

(facts "about make-query-from-params"
       (fact "it transforms a comma-separated select into a vector"
             (make-query-from-params {:dimensions {}
                                      :clauses {:$select "name,state"}})
             => (contains {:select ["name" "state"]}))

       (fact "it transforms a comma-separated order into a sorted map"
             (make-query-from-params {:dimensions {}
                                      :clauses {:$orderBy "name,state"}})
             => (contains {:order (sorted-map "name" 1 "state" 1)}))

       (fact "it turns DESC in orderBy into a -1 for order"
             (make-query-from-params {:dimensions {}
                                      :clauses {:$orderBy "name desc, state"}})
             => (contains {:order (sorted-map "name" -1 "state" 1)}))

       (fact "it stores dimensions in the query"
             (make-query-from-params {:dimensions {:state_abbr "AL"}
                                      :clauses {}})
             => (contains {:dimensions {:state_abbr "AL"}})))

(facts "about query->mongo"
       (fact "it parses the where value and adds dimensions"
             (query->mongo (map->Query {:where "income > 10000"
                                        :dimensions {:state "WY"}
                                        :limit 100 :offset 0 :order {}}))
             => (contains {:query {:income {"$gt" 10000}
                                   :state "WY"}}))

       (fact "it adds fields iff :select exists"
             (query->mongo (map->Query {:select ["name" "state"]
                                        :limit 100 :offset 0 :order {} :where ""
                                        }))
             => (contains {:fields ["name" "state"]})

             (query->mongo (map->Query {:limit 100 :offset 0 :order {} :where ""}))
             =not=> #(contains? % :fields)))
       
