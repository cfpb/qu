(ns integration.mongo-query-test
  "The 'integration_test' dataset is designed to simplify testing.
  Its data values are simple and map to the row number.

  The tests below are not designed to test MongoDB. Rather, they provide
  a regression layer to ensure the correct interaction between the modules
  that prepare queries for the DB and the modules that use those queries and
  fetch data."
  (:require [midje.sweet :refer :all]
            [cfpb.qu.data :as data]
            [cfpb.qu.loader :as loader]
            [cfpb.qu.query :as query :refer [params->Query]]
            [cfpb.qu.query.cache :as qc]
            [clojure.core.cache :as cache]
            [monger.core :as mongo]))

(def db "integration_test")
(def coll "incomes")

(with-state-changes [(before :facts (do
                                      (data/connect-mongo)
                                      (loader/load-dataset db)
                                      (def metadata (data/get-metadata db))
                                      (def slicedef (get-in metadata [:slices :incomes]))))
                     (after :facts (data/disconnect-mongo))]

  (facts "about metadata"

    (fact "get-dataset-names contains expected dataset names"
      (data/get-dataset-names) => (contains ["integration_test"] :gaps-ok))

    (fact "get-datasets returns sequence of maps"
      (let [ds (data/get-datasets)]
        (>= (count ds) 1) => true
        (map :name ds) => (contains ["integration_test"] :gaps-ok)))

    (fact "get-metadata returns metadata for a dataset"
        (:name metadata) => "integration_test"
        (keys metadata) => (contains [:_id :last-modified :name :database :dir
                                      :info :concepts :slices :compression-map]
                                     :in-any-order)
        (get-in metadata [:compression-map :incomes]) =not=> nil
        (get-in metadata [:info :copyright]) => #"public domain"
        (get-in metadata [:slices :incomes :metrics])
        => ["tax_returns" "adjusted_gross_income" "date_observed"]))

  ;; get-find and get-aggregation facts are simply sanity checks
  (facts "about get-find"
    (fact "returns a QueryResult object with total, size, and data (Seq of maps)"
      (let [limit 2
            query {:query {} :fields {} :limit limit :skip 0 :sort {}}
            result (data/get-find db coll query)]
        (> (:total result) limit) => true
        (:size result) => limit
        (count (:data result)) => limit)))

  (facts "about execute"
    (fact "passes a Query object to MongoDB and returns results"
      (let [q (params->Query {:state_abbr "NC"} metadata :incomes)
            result (query/execute q)
            query_result (:result result)
            first-doc (first (:data query_result))]
        first-doc => (contains {:tax_returns 1 :county "County 1" :state_abbr "NC" :state_name "North Carolina"})
        (> (:total query_result) 0) => true
        (> (:size query_result) 0) => true))

    (fact "returns only fields in $select, if specified"
      (let [q (params->Query {:$select "state_name,county,tax_returns" :state_abbr "NC"}
                             metadata :incomes)
            result (query/execute q)
            query_result (:result result)
            first-doc (first (:data query_result))]
        first-doc => {:tax_returns 1 :county "County 1" :state_name "North Carolina"}))

    (fact "limits number of returned documents if $limit is specified"
      (let [limit 10
            q (params->Query {:$limit limit} metadata :incomes)
            result (query/execute q)
            query_result (:result result)]
        (count (:data query_result)) => limit
        (:size query_result) => limit
        (> (:total query_result) limit) => true))

    (fact "skips documents if $offset is specified"
      (let [offset 1
            all (params->Query {:$orderBy "tax_returns"} metadata :incomes)
            q (params->Query {:$offset offset :$orderBy "tax_returns"} metadata :incomes)
            all_result (:result (query/execute all))
            query_result (:result (query/execute q))
            first-doc (first (:data query_result))]
        (:tax_returns first-doc) => (+ offset 1)
        (:size query_result) => (- (:size all_result) offset)
        (:total query_result) => (:total all_result)))

    (fact "filters documents if $where is specified"
      (let [upper-bound 9
            where (str "tax_returns <= " upper-bound)
            q (params->Query {:$where where} metadata :incomes)
            result (query/execute q)
            query_result (:result result)]
        (:total query_result) => upper-bound
        (:size query_result) => upper-bound
        (:tax_returns (last (:data query_result))) => upper-bound)))

  (facts "about execute and aggregation"
    (let [query (params->Query {:$select "state_abbr, SUM(tax_returns), COUNT(tax_returns), MIN(tax_returns), MAX(tax_returns)", :$group "state_abbr", :$orderBy "state_abbr"} metadata :incomes)
          agg-map (query/mongo-aggregation (query/prepare query))
          cache (qc/create-query-cache)]

      (with-state-changes [(before :facts (cache/evict cache query))]
        (fact "returns a :computing result at first"
              (let [q (params->Query {:$select "state_abbr, SUM(tax_returns), COUNT(tax_returns), MIN(tax_returns), MAX(tax_returns)", :$group "state_abbr", :$orderBy "state_abbr"} metadata :incomes)
                    result (query/execute q)
                    query_result (:result result)]
                (:data query_result) => :computing)))

      (with-state-changes [(before :facts (qc/add-to-cache cache agg-map))]
        (fact "once added to the cache, returns result containing aggregation"
              (let [q query
                    result (query/execute q)
                    query_result (:result result)]
                (:size query_result) => 4
                (:total query_result) => 4
                (count (:data query_result)) => 4
                (:data query_result) => (just [{:sum_tax_returns 15.0, :count_tax_returns 5.0, :min_tax_returns 1.0, :max_tax_returns 5.0, :state_abbr "NC"},
                                               {:sum_tax_returns 65.0, :count_tax_returns 5.0, :min_tax_returns 11.0, :max_tax_returns 15.0, :state_abbr "NY"},
                                               {:sum_tax_returns 40.0, :count_tax_returns 5.0, :min_tax_returns 6.0, :max_tax_returns 10.0, :state_abbr "PA"},
                                               {:sum_tax_returns 33.0, :count_tax_returns 2.0, :min_tax_returns 16.0, :max_tax_returns 17.0, :state_abbr "DC"}] :in-any-order))))))
  
  (facts "about execute and error handling"

    (fact "result contains :error when invalid $select is specified"
      (let [q (params->Query {:$select "trick_name"} metadata :incomes)
            result (query/execute q)]
        (:result result) => []
        (first (get-in result [:errors :select])) => (contains "\"trick_name\" is not a valid field")))

    (fact "result contains :error when invalid $where is specified"
      (let [q (params->Query {:$where "inventor = 'plywood_hoods'", :$orderBy "difficulty"} metadata :incomes)
            result (query/execute q)
            errors (:errors result)]
        (:result result) => []
        (first (:where errors)) => (contains "\"inventor\" is not a valid field")
        (first (:orderBy errors)) => (contains "\"difficulty\" is not a valid field")))

    (fact "result contains :error when invalid $limit or $offset is specified"
      (let [q (params->Query {:$limit "a" :$offset "b"} metadata :incomes)
            result (query/execute q)]
        (:result result) => []
        (first (get-in result [:errors :limit])) => (contains "use an integer")
        (first (get-in result [:errors :offset])) => (contains "use an integer")))

    (fact "result contains :error when $group is present but $select is not"
      (let [q (params->Query {:$group "state_abbr"} metadata :incomes)
            result (query/execute q)]
        (:result result) => []
        (first (get-in result [:errors :group])) => (contains "must have a select clause")))

    (fact "result contains :error when invalid $group is specified"
      (let [q (params->Query {:$select "state_abbr", :$group "cherrypicker"} metadata :incomes)
            result (query/execute q)]
        (:result result) => []
        (first (get-in result [:errors :group])) => (contains "\"cherrypicker\" is not a valid field")
        (last (get-in result [:errors :group])) => (contains "\"cherrypicker\" is not a dimension")))))
