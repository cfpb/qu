(ns cfpb.qu.query.mongo-test
  (:refer-clojure :exclude [sort])
  (:require [midje.sweet :refer :all]
            [cfpb.qu.query.mongo :refer :all]))

(facts "about sort"
       (fact "it transforms :orderBy into a sorted-map"
             (:mongo (sort {:orderBy "name"})) =>
             (contains {:sort (sorted-map "name" 1)})

             (:mongo (sort {:orderBy "state, name"})) =>
             (contains {:sort (sorted-map "state" 1 "name" 1)})

             (:mongo (sort {:orderBy "state DESC, name"})) =>
             (contains {:sort (sorted-map "state" -1 "name" 1)})))

(facts "about match"
       (fact "it adds errors if it cannot parse"
             (:errors (match {:where "bad where"})) =>
             (contains {:where anything}))

       (fact "it transforms :where into :match"
             (:mongo (match {:where "a > 2"})) =>
             (contains {:match {:a {"$gt" 2}}})))

(facts "about project"
       (fact "it adds errors if it cannot parse"
             (:errors (project {:select "bad select"})) =>
             (contains {:select anything}))
       
       (fact "it transforms :select into :project"
             (:mongo (project {:select "name, city"})) =>
             (contains {:project {:name 1, :city 1}})))

(facts "about group"
       (fact "it transforms COUNT selects into $sum: 1"
             (:mongo (group {:select "state, COUNT(county)" :group "state"})) =>
             (contains {:group {:_id {:state "$state"}
                                :count_county {"$sum" 1}}})))
