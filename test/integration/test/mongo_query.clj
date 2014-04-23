(ns ^:integration integration.test.mongo-query
  "The 'integration_test' dataset is designed to simplify testing.
  Its data values are simple and map to the row number.

  The tests below are not designed to test MongoDB. Rather, they provide
  a regression layer to ensure the correct interaction between the modules
  that prepare queries for the DB and the modules that use those queries and
  fetch data."
  (:require [clojure.test :refer :all]
            [qu.test-util :refer :all]
            [qu.data :as data]
            [qu.loader :as loader]
            [qu.query :as query :refer [params->Query]]
            [qu.cache :as qc]
            [clojure.core.cache :as cache]
            [monger.collection :as coll]))

(def db "integration_test")
(def coll "incomes")

(use-fixtures :once (mongo-setup-fn db))

(deftest ^:integration test-metadata
  (testing "get-dataset-names contains expected dataset names"
    (does-contain (set (data/get-dataset-names)) ["integration_test"]))

  (testing "get-datasets returns sequence of maps"
    (let [ds (data/get-datasets)]
      (is (>= (count ds) 1))
      (does-contain (set (map :name ds)) ["integration_test"])))

  (testing "get-metadata returns metadata for a dataset"
    (let [metadata (data/get-metadata db)]
      (does= (:name metadata) "integration_test")
      (does-contain (set (keys metadata))
                    [:_id :last-modified :name :database :dir
                     :info :concepts :slices :compression-map])
      (is (not (nil? (get-in metadata [:compression-map :incomes]))))
      (does-re-find (get-in metadata [:info :copyright]) #"public domain")
      (does= (get-in metadata [:slices :incomes :metrics])
             ["tax_returns" "adjusted_gross_income" "date_observed"]))))

;; get-find and get-aggregation deftest are simply sanity checks
(deftest ^:integration test-get-find
  (testing "returns a QueryResult object with total, size, and data (Seq of maps)"
    (let [limit 2
          query {:query {} :fields {} :limit limit :skip 0 :sort {}}
          result (data/get-find db coll query)]
      (is (> (:total result) limit))
      (does=
       (:size result) limit
       (count (:data result)) limit))))


(deftest ^:integration test-execute
  (testing "passes a Query object to MongoDB and returns results"
    (let [metadata (data/get-metadata db)
          q (query/prepare (params->Query {:state_abbr "NC"} metadata :incomes))
          query_result (query/execute q)
          first-doc (first (:data query_result))]
      (does-contain first-doc
                    {:tax_returns 1 :county "County 1"
                     :state_abbr "NC" :state_name "North Carolina"})
      (is (> (:total query_result) 0))
      (is (> (:size query_result) 0))))

  (testing "returns only fields in $select, if specified"
    (let [metadata (data/get-metadata db)
          q (query/prepare (params->Query {:$select "state_name,county,tax_returns" :state_abbr "NC"}
                                          metadata :incomes))
          query_result (query/execute q)
          first-doc (first (:data query_result))]
      (does= first-doc {:tax_returns 1 :county "County 1" :state_name "North Carolina"})))

  (testing "limits number of returned documents if $limit is specified"
    (let [limit 10
          metadata (data/get-metadata db)
          q (query/prepare (params->Query {:$limit limit} metadata :incomes))
          query_result (query/execute q)]
      (does=
       (count (:data query_result)) limit
       (:size query_result) limit)
      (is (> (:total query_result) limit))))

  (testing "skips documents if $offset is specified"
    (let [offset 1
          metadata (data/get-metadata db)
          all (query/prepare (params->Query {:$orderBy "tax_returns"} metadata :incomes))
          q (query/prepare (params->Query {:$offset offset :$orderBy "tax_returns"} metadata :incomes))
          all_result (query/execute all)
          query_result (query/execute q)
          first-doc (first (:data query_result))]
      (does=
       (:tax_returns first-doc) (+ offset 1)
       (:size query_result) (- (:size all_result) offset)
       (:total query_result) (:total all_result))))

  (testing "filters documents if $where is specified"
    (let [upper-bound 9
          where (str "tax_returns <= " upper-bound)
          metadata (data/get-metadata db)
          q (query/prepare (params->Query {:$where where} metadata :incomes))
          query_result (query/execute q)]
      (does=
       (:total query_result) upper-bound
       (:size query_result) upper-bound
       (:tax_returns (last (:data query_result))) upper-bound)))

  (testing "ensure no regressions with complex boolean expressions"
    (let [where "tax_returns <= 3 AND state_abbr = 'NC' AND county = 'County 3'"
          metadata (data/get-metadata db)
          q (query/prepare (params->Query {:$where where} metadata :incomes))
          query_result (query/execute q)]
      (does=
       (:total query_result) 1
       (:size query_result) 1
       (:tax_returns (first (:data query_result))) 3
       (:state_name (first (:data query_result))) "North Carolina"))

    (let [where "tax_returns <= 3 OR state_abbr = 'PA' OR county = 'County 15'"
          metadata (data/get-metadata db)
          q (query/prepare (params->Query {:$where where} metadata :incomes))
          query_result (query/execute q)
          data (:data query_result)]      
      (is (some #(<= (:tax_returns %) 3) data))
      (is (some #(= (:state_abbr %) "PA") data))
      (is (some #(= (:county %) "County 15") data)))

    (let [where "adjusted_gross_income >= 200 AND (tax_returns <= 3 OR state_abbr = 'PA' OR county = 'County 15')"
          metadata (data/get-metadata db)
          q (query/prepare (params->Query {:$where where} metadata :incomes))
          query_result (query/execute q)
          data (:data query_result)]      
      (is (every? #(>= (:adjusted_gross_income %) 200) data))
      (is (some #(<= (:tax_returns %) 3) data))
      (is (some #(= (:state_abbr %) "PA") data))
      (is (some #(= (:county %) "County 15") data)))))


(deftest ^:integration test-execute-and-aggregation
  (let [metadata (data/get-metadata db)
        query (-> {:dataset db
                   :slice :incomes
                   :select (str "state_abbr, SUM(tax_returns), COUNT(tax_returns), "
                                "MIN(tax_returns), MAX(tax_returns)")
                   :group "state_abbr"
                   :orderBy "state_abbr"}
                  query/make-query
                  query/prepare)
        query (query/prepare query)
        agg-map (query/mongo-aggregation query)
        cache (qc/create-query-cache)]

    (testing "returns a :computing result at first"
      (cache/evict cache query)
      (let [q (params->Query {:$select "state_abbr, SUM(tax_returns), COUNT(tax_returns), MIN(tax_returns), MAX(tax_returns)", :$group "state_abbr", :$orderBy "state_abbr"} metadata :incomes)
            query_result (query/execute (query/prepare q))]
        (is (:computing query_result))))

    (testing "once added to the cache, returns result containing aggregation"
      (qc/add-to-cache cache agg-map)
      (let [q query
            query_result (query/execute (query/prepare q))]
        (does=
         (:size query_result) 4
         (:total query_result) 4
         (count (:data query_result)) 4
         (->> (:data query_result)
              (sort-by :state_abbr)
              (map #(dissoc % :_id)))
         [{:sum_tax_returns 33, :count_tax_returns 2, :min_tax_returns 16,
           :max_tax_returns 17, :state_abbr "DC"},
          {:sum_tax_returns 15, :count_tax_returns 5, :min_tax_returns 1,
           :max_tax_returns 5, :state_abbr "NC"},
          {:sum_tax_returns 65, :count_tax_returns 5, :min_tax_returns 11,
           :max_tax_returns 15, :state_abbr "NY"},
          {:sum_tax_returns 40, :count_tax_returns 5, :min_tax_returns 6,
           :max_tax_returns 10, :state_abbr "PA"}])))))


(deftest ^:integration test-execute-and-error-handling
  (let [metadata (data/get-metadata db)]
    (testing "result contains :error when invalid $select is specified"
      (let [q (query/prepare (params->Query {:$select "trick_name"} metadata :incomes))
            result (query/execute q)]
        (does= result [])
        (does-contain
         (get-in q [:errors :select])
         "\"trick_name\" is not a valid field.")))

    (testing "result contains :error when invalid $where is specified"
      (let [q (query/prepare (params->Query {:$where "inventor = 'plywood_hoods'", :$orderBy "difficulty"}
                                            metadata :incomes))
            result (query/execute q)
            errors (:errors q)]
        (does= result [])
        (does-contain (:where errors) "\"inventor\" is not a valid field.")
        (does-contain (:orderBy errors) "\"difficulty\" is not a valid field.")))

    (testing "result contains :error when invalid $limit or $offset is specified"
      (let [q (query/prepare (params->Query {:$limit "a" :$offset "b"} metadata :incomes))
            result (query/execute q)]
        #_(does= result [])
        (does-contain (get-in q [:errors :limit]) "Please use an integer.")
        (does-contain (get-in q [:errors :offset]) "Please use an integer.")))

    (testing "result contains :error when $group is present but $select is not"
      (let [q (query/prepare (params->Query {:$group "state_abbr"} metadata :incomes))
            result (query/execute q)]
        (does= result [])
        (does-contain (get-in q [:errors :group])
                      "You must have a select clause to use grouping.")))

    (testing "result contains :error when invalid $group is specified"
      (let [q (query/prepare (params->Query {:$select "state_abbr", :$group "cherrypicker"} metadata :incomes))
            result (query/execute q)]
        (does= result [])
        (does-contain (get-in q [:errors :group]) "\"cherrypicker\" is not a valid field.")
        (does-contain (get-in q [:errors :group]) "\"cherrypicker\" is not a dimension.")))))

;; (run-tests)
