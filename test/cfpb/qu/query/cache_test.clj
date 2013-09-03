(ns cfpb.qu.query.cache-test
  (:refer-clojure :exclude [sort])
  (:require [midje.sweet :refer :all]
            [cfpb.qu.query :as q]
            [cfpb.qu.query.cache :refer :all]))

(facts "about query-to-key"
       (let [query1 (q/map->Query {:select "state_id, county_id, MAX(tax_returns)" :group "state_id, county_id" :metadata {:database "test"} :slice "test"})
             query2 (q/map->Query {:select "state_id,county_id,MAX(tax_returns)" :group "state_id,county_id" :metadata {:database "test"} :slice "test"})]
         
         (fact "it eliminates space in the SELECT and GROUP BY fields"
               (query-to-key query1) => (query-to-key query2))

         (fact "different WHERE queries make different keys"
               (query-to-key (assoc query1 :where "state_id = 1")) =not=>
               (query-to-key (assoc query1 :where "state_id = 2")))

         (fact "different ORDER BY queries make the same key"
               (query-to-key (assoc query1 :orderBy "state_id")) =>
               (query-to-key (assoc query1 :orderBy "max_tax_returns")))

         (fact "different LIMIT and OFFSET queries make the same key"
               (query-to-key (assoc query1 :limit 10)) =>
               (query-to-key (assoc query1 :limit 20 :offset 10)))))
