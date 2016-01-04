(ns qu.test.query.validation
  (:require [clojure.test :refer :all]
            [qu.test-util :refer :all]
            [qu.query :as query]
            [qu.query.validation :as v]))

(deftest test-validate
  (let [slicedef {:dimensions ["state_abbr" "county" "county_code" "city" "city_abbr"]
                  :metrics ["tax_returns"]
                  :max-group-fields 3}
        metadata {:slices {:county_taxes slicedef}
                  :concepts {:county {:properties {:population {:type "number"}
                                                   :state {:type "string"}}}}}
        q (fn [& {:as query}]
            (merge {:slicedef slicedef
                    :slice :county_taxes
                    :metadata metadata}
                   query))
        errors (comp :errors v/validate)]

    (testing "it errors when it cannot parse SELECT"
      (does-contain (errors (q :select "what what")) :select))

    (testing "it errors when you have an aggregation in SELECT without a GROUP"               
      (let [query (q :select "state_abbr, SUM(tax_returns)")]
        (does-contain (errors query) :select)
        (does-not-contain (errors (assoc query :group "state_abbr")) :select)))

    (testing "it errors if you have an unaggregated SELECT field without it being in GROUP"
      (let [query (q :select "state_abbr, county, SUM(tax_returns)"
                     :group "state_abbr")]
        (does-contain (errors query) :select)
        (does-not-contain (errors (assoc query :group "state_abbr, county")) :select)))

    (testing "it errors if you reference a field that is not in the slice"
      (does-contain (errors (q :select "foo")) :select)
      (does-contain (errors (q :select "state_abbr, SUM(foo)" :group "state_abbr")) :select)
      (does-contain (errors (q :select "foo.population")) :select))
         
    (testing "it errors if it cannot parse GROUP"
      (does-contain (errors (q :select "state_abbr" :group "what what")) :group))

    (testing "it errors if if the number of fields in GROUP exceeds the maximum allowed"
      (let [query (q :select "state_abbr, county, county_code, city, city_abbr, SUM(tax_returns)"
                     :group "state_abbr, county, county_code, city, city_abbr")
            group-errors (:group (errors query))]
        (does-contain group-errors "Number of group fields exceeds maximum allowed (3).")))

    (testing "it errors if you use GROUP without SELECT"
      (let [query (q :group "state_abbr")]
        (does-contain (errors query) :group)
        (does-not-contain (errors (assoc query :select "state_abbr")) :group)))

    (testing "it errors if you GROUP on something that is not a dimension"
      (let [query (q :select "tax_returns" :group "tax_returns")]
        (does-contain (errors query) :group)))

    (testing "it errors if it cannot parse WHERE"
      (does-contain (errors (q :where "what what")) :where))

    (testing "it errors if it cannot parse ORDER BY"
      (does-contain (errors (q :orderBy "what what")) :orderBy)
      (does-not-contain (errors (q :orderBy "state_abbr DESC")) :orderBy))
         
    (testing "it errors if limit is not an integer string"
      (does-contain (errors (q :limit "ten")) :limit)
      (does-not-contain (errors (q :limit "10")) :limit))

    (testing "it does not error if limit is greater than 1000"
      (does-not-contain (errors (q :limit "1001"))
                        {:limit ["The maximum limit is 1000."]}))
         
    (testing "it errors if offset is not an integer string"
      (does-contain (errors (q :offset "ten")) :offset)
      (does-not-contain (errors (q :offset "10")) :offset))

    (testing "it errors if page is not a positive integer string"
      (does-contain (errors (q :page "ten")) :page)
      (does-contain (errors (q :page "-1")) :page)
      (does-contain (errors (q :page "0")) :page)
      (does-not-contain (errors (q :page "10")) :page))))

;; (run-tests)
