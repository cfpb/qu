(ns cfpb.qu.query.mongo-test
  (:refer-clojure :exclude [sort])
  (:require [midje.sweet :refer :all]
            [cfpb.qu.query.mongo :refer :all]))

(facts "about sort"
       (fact "it transforms :orderBy into a sorted-map"
             (:mongo (sort {:orderBy "name"})) =>
             (contains {:sort (sorted-map :name 1)})

             (:mongo (sort {:orderBy "state, name"})) =>
             (contains {:sort (sorted-map :state 1 :name 1)})

             (:mongo (sort {:orderBy "state DESC, name"})) =>
             (contains {:sort (sorted-map :state -1 :name 1)})))

(facts "about match"
       (fact "it transforms :where into :match"
             (:mongo (match {:where "a > 2"})) =>
             (contains {:match {:a {"$gt" 2}}})))

(facts "about project"
       (fact "it transforms :select into :project"
             (:mongo (project {:select "name, city"})) =>
             (contains {:project {:name 1, :city 1}}))

       (fact "it transforms aggregations"
             (:mongo (project {:select "city, MAX(income)", :group "city"})) =>
             (contains {:project {:fields [:city :max_income]
                                  :aggregations {:max_income ["max" "income"]}}})))

(facts "about group"
       (fact "it makes a list of all fields to group by"
             (:mongo (group {:select "state, county, COUNT()" :group "state, county"})) =>
             (contains {:group [:state :county]})))

(facts "about process"
       (let [slicedef {:dimensions ["state_abbr" "county"]
                       :metrics ["tax_returns"]}
             errors (comp :errors process)]
         
         (fact "it errors if you use a field in WHERE that does not exist"
               (errors {:where "foo > 0" :slicedef slicedef})
               => (contains {:where anything}))

         (fact "it errors if you try to ORDER BY a field that does not exist"
               (errors {:orderBy "foo" :slicedef slicedef})
               => (contains {:orderBy anything}))

         (fact "it does not error when you ORDER BY an aggregated field"
               (errors {:group "state_abbr"
                        :select "state_abbr, COUNT()"
                        :orderBy "count"
                        :slicedef slicedef})
               =not=> (contains {:orderBy anything}))))
