(ns cfpb.qu.query-test
  (:require [midje.sweet :refer :all]
            [cfpb.qu.query :refer :all]
            [cfpb.qu.query.mongo :as mongo]))

(def metadata
  {:slices {:county_taxes {:dimensions ["state" "county"]
                           :metrics ["tax_returns" "population"]}}})

(defn make-test-query
  [q]
  (merge {:metadata metadata
          :slicedef (get-in metadata [:slices :county_taxes])
          :limit "0" :offset "0"} q))

(def query (make-test-query {}))

(fact "parse-params pulls out clauses"
      (let [params {:$select "age,race" :$foo "1"}]
        (parse-params params) => {:$select "age,race"}))

(facts "about is-aggregation?"
       (fact "returns true if we have a group key"
             {:group "state"} => is-aggregation?)

       (fact "returns false if we don't have a group key"
             {} =not=> is-aggregation?))

(facts "about mongo-find"
       (fact "it populates fields if :select exists"
             (mongo-find (mongo/process (make-test-query {:select "county, state"})))
             => (contains {:fields {:county 1 :state 1}})

       (fact "it returns empty fields if :select does not exist"
             (mongo-find query)
             => (contains {:fields {}}))

        (fact "it returns empty sort if :sort does not exist"
             (mongo-find query)
             => (contains {:sort {}}))))

(facts "about mongo-aggregation"
       (fact "it creates a map-reduce query for Mongo"
             (let [query (mongo/process (make-test-query {:select "state, SUM(population)"
                                                     :limit 100
                                                     :offset 0
                                                     :where "land_area > 1000000"
                                                     :orderBy "state"
                                                     :group "state"}))]
               (mongo-aggregation query) =>
               (contains {:group [:state]
                          :aggregations {:sum_population ["sum" "population"]}
                          :sort {:state 1}
                          :limit 100
                          :offset 0}))))

(facts "about execute"
       (let [query (merge query {:limit 100 :offset 0 :page 1 :errors {}
                                 :dataset ..dataset.. :slice ..collection..
                                 :aliases {} :reverse-aliases {}})]
         (fact "it calls data/get-find if is-aggregation? is false"
               (execute query) => (contains {:result ..get-find..})
               (provided
                (#'cfpb.qu.query.mongo/process query) => query
                (is-aggregation? query) => false
                (mongo-find query) => {}
                (#'cfpb.qu.data/get-find ..dataset.. ..collection.. {}) => ..get-find..))

         (fact "it calls data/get-aggregation if is-aggregation? is true"
               (execute query) => (contains {:result ..get-aggregation..})
               (provided
                (#'cfpb.qu.query.mongo/process query) => query
                (is-aggregation? query) => true
                (mongo-aggregation query) => {}
                (#'cfpb.qu.data/get-aggregation ..dataset.. ..collection.. {}) => ..get-aggregation..))))
