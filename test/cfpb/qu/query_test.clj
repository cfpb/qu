(ns cfpb.qu.query-test
  (:require [midje.sweet :refer :all]
            [cfpb.qu.query :refer :all]
            [cfpb.qu.query.mongo :as mongo]))

(defn make-query
  [q]
  (merge {:slicedef {:dimensions ["state" "county"]
                     :metrics ["tax_returns" "population"]}
          :limit "0" :offset "0"} q))

(def query (make-query {}))

(fact "parse-params puts clauses under :clauses"
      (let [params {:$select "age,race"}]
        (parse-params params {}) => (contains {:clauses {:$select "age,race"}})))

(facts "about is-aggregation?"
       (fact "returns true if we have a group key"
             {:group "state"} => is-aggregation?)

       (fact "returns false if we don't have a group key"
             {} =not=> is-aggregation?))

(facts "about params->Query"
       (fact "it stores dimensions in the query"
             (params->Query {:state "AL"}
                            {:dimensions ["state" "county"]})
             => (contains {:dimensions {:state "AL"}})))

(facts "about mongo-find"
       (fact "it populates fields if :select exists"
             (mongo-find (mongo/process (make-query {:select "county, state"})))
             => (contains {:fields {:county 1 :state 1}})

       (fact "it returns empty fields if :select does not exist"
             (mongo-find query)
             => (contains {:fields {}}))

        (fact "it returns empty sort if :sort does not exist"
             (mongo-find query)
             => (contains {:sort {}}))))

(facts "about mongo-aggregation"
       (fact "it creates a chain of filters for Mongo"
             (let [query (mongo/process (make-query {:select "state, SUM(population)"
                                                     :limit 100
                                                     :offset 0
                                                     :where "land_area > 1000000"
                                                     :orderBy "state"
                                                     :group "state"}))]
               (mongo-aggregation query) =>
               [{"$match" {:land_area {"$gt" 1000000}}}
                {"$group" {:_id {:state "$state"} :sum_population {"$sum" "$population"}}}
                {"$project" {"state" "$_id.state", "sum_population" "$sum_population"}}
                {"$sort" {"state" 1}}
                {"$skip" 0}
                {"$limit" 100}])))

(facts "about execute"
       (fact "it calls data/get-find if is-aggregation? is false"
             (execute ..collection.. query) => (contains {:result ..get-find..})
             (provided
                (#'cfpb.qu.query.mongo/process query) => query
                (is-aggregation? query) => false
                (mongo-find query) => {}
                (#'cfpb.qu.data/get-find ..collection.. {}) => ..get-find..))

       (fact "it calls data/get-aggregation if is-aggregation? is true"
             (execute ..collection.. query) => (contains {:result ..get-aggregation..})
             (provided
              (#'cfpb.qu.query.mongo/process query) => query
              (is-aggregation? query) => true
              (mongo-aggregation query) => {}
              (#'cfpb.qu.data/get-aggregation ..collection.. {}) => ..get-aggregation..)))
