(ns cfpb.qu.query-test
  (:require [midje.sweet :refer :all]
            [cfpb.qu.query :refer :all]
            [cfpb.qu.query.mongo :as mongo]))

(fact "parse-params puts clauses under :clauses"
      (let [params {:$select "age,race"}]
        (parse-params params {}) => (contains {:clauses {:$select "age,race"}})))

(facts "about is-aggregation?"
       (fact "returns true if we have a group key"
             {:group "state_abbr"} => is-aggregation?)

       (fact "returns false if we don't have a group key"
             {} =not=> is-aggregation?))

(facts "about params->Query"
       (fact "it stores dimensions in the query"
             (params->Query {:state_abbr "AL"}
                            {:dimensions ["state_abbr" "county"]})
             => (contains {:dimensions {:state_abbr "AL"}})))

(facts "about mongo-find"
       (fact "it adds fields iff :select exists"
             (mongo-find (mongo/process {:select "name, state"}))
             => (contains {:fields {:name 1 :state 1}})

             (mongo-find {})
             =not=> #(contains? % :fields)))

(facts "about mongo-aggregation"
       (fact "it creates a chain of filters for Mongo"
             (let [query (mongo/process {:select "state, SUM(population)"
                                         :limit 100
                                         :offset 0
                                         :where "land_area > 1000000"
                                         :orderBy "state"
                                         :group "state"})]
               (mongo-aggregation query) =>
               [{"$match" {:land_area {"$gt" 1000000}}}
                {"$group" {:_id {:state "$state"} :sum_population {"$sum" "$population"}}}
                {"$project" {"state" "$_id.state", "sum_population" "$sum_population"}}
                {"$sort" {"state" 1}}
                {"$skip" 0}
                {"$limit" 100}])))

(facts "about execute"
       (fact "it calls data/get-find if is-aggregation? is false"
             (execute ..collection.. {}) => {:result ..get-find..}
             (provided
              (is-aggregation? {}) => false
              (mongo-find {}) => {}
              (#'cfpb.qu.data/get-find ..collection.. {}) => ..get-find..))

       (fact "it calls data/get-aggregation if is-aggregation? is true"
             (execute ..collection.. {}) => {:result ..get-aggregation..}
             (provided
              (is-aggregation? {}) => true
              (mongo-aggregation {}) => {}
              (#'cfpb.qu.data/get-aggregation ..collection.. {}) => ..get-aggregation..)))
