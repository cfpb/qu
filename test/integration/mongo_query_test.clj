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
            [monger.core :as mongo]))

(def db "integration_test")
(def coll "incomes")

(with-state-changes [(before :facts (do
                                      (data/connect-mongo)
                                      (loader/load-dataset db)))
                     (after :facts (data/disconnect-mongo))]

  (facts "about metadata"

    (fact "get-dataset-names contains expected dataset names"
      (data/get-dataset-names) => (contains ["county_taxes", "integration_test"] :gaps-ok))

    (fact "get-datasets returns sequence of maps"
      (let [ds (data/get-datasets)]
        (>= (.size ds) 2) => true
        (map :name ds) => (contains ["county_taxes" "integration_test"] :gaps-ok)))

    (fact "get-metadata returns metadata for a dataset"
      (let [md (data/get-metadata db)]
        (:name md) => "integration_test"
        (keys md) => (contains [:_id :name :info :concepts :slices :tables])
        (get-in md [:tables :incomes]) =not=> nil
        (get-in md [:slices :incomes :metrics]) => ["tax_returns" "adjusted_gross_income"])))

  (facts "about get-find"

    (fact "returns a QueryResult object with total, size, and data (Seq of maps)"
      (let [ limit 2
             query {:query {} :fields {} :limit limit :skip 0 :sort {}}
             result (data/get-find db coll query)]
        (> (:total result) limit ) => true
        (:size result) => limit
        (.size (:data result)) => limit)))

  (facts "about execute"
    (def md (data/get-metadata db))
    (def slicedef (get-in md [:slices :incomes]))

    (fact "passes a Query object to MongoDB and returns results"
      (let [q (params->Query {} slicedef)
            result (query/execute db coll q)
            query_result (:result result)
            first-doc (first (:data query_result))]
        first-doc => (contains {:tax_returns 1 :county "County 1" :state_abbr "NC"})
        (> (:total query_result) 0) => true
        (> (:size query_result) 0) => true))

    (fact "returns only fields in $select, if specified"
      (let [q (params->Query {:$select "state_abbr,county,tax_returns"} slicedef)
            result (query/execute db coll q)
            query_result (:result result)
            first-doc (first (:data query_result))]
        first-doc => {:tax_returns 1 :county "County 1" :state_abbr "NC"}))

    (fact "limits number of returned documents if $limit is specified"
      (let [limit 10
            q (params->Query {:$limit limit} slicedef)
            result (query/execute db coll q)
            query_result (:result result)]
        (.size (:data query_result)) => limit
        (:size query_result) => limit
        (> (:total query_result) limit) => true))

    (fact "skips documents if $offset is specified"
      (let [offset 1
            all (params->Query {} slicedef)
            q (params->Query {:$offset offset} slicedef)
            all_result (:result (query/execute db coll all))
            query_result (:result (query/execute db coll q))
            first-doc (first (:data query_result))]
        (:tax_returns first-doc) => (+ offset 1)
        (:size query_result) => (- (:size all_result) offset)
        (:total query_result) => (:total all_result)))

    (fact "filters documents if $where is specified"
      (let [upper-bound 9
            where (str "tax_returns <= " upper-bound)
            q (params->Query {:$where where} slicedef)
            result (query/execute db coll q)
            query_result (:result result)]
        (:total query_result) => upper-bound
        (:size query_result) => upper-bound
        (:tax_returns (last (:data query_result))) => upper-bound))

    (fact "result contains :error when invalid select is specified"
      (let [q (params->Query {:$select "miami_hopper"} slicedef)
            result (query/execute db coll q)
            query_result (:result result)]
        query_result => []
        (first (get-in result [:errors :select])) => (contains "is not a valid field")))))
