(ns cfpb.qu.query.validation-test
  (:require [midje.sweet :refer :all]
            [cfpb.qu.query :as query]
            [cfpb.qu.query.validation :refer :all]))

(facts "about validate"
       (let [slicedef {:dimensions ["state_abbr" "county"]
                       :metrics ["tax_returns"]}
             metadata {:slices {:county_taxes slicedef}
                       :concepts {:county {:properties {:population {:type "number"}
                                                        :state {:type "string"}}}}}
             q (fn [& {:as query}]
                 (merge {:slicedef slicedef
                         :slice :county_taxes
                         :metadata metadata}
                        query))
             errors (comp :errors validate)]

         (fact "it errors when it cannot parse SELECT"
               (errors (q :select "what what")) =>
               (contains {:select anything}))

         (fact "it errors when you have an aggregation in SELECT without a GROUP"               
               (let [query (q :select "state_abbr, SUM(tax_returns)")]
                 (errors query) => (contains {:select anything})

                 (errors (assoc query :group "state_abbr")) =not=>
                 (contains {:select anything})))

         (fact "it errors if you have an unaggregated SELECT field without it being in GROUP"
               (let [query (q :select "state_abbr, county, SUM(tax_returns)"
                              :group "state_abbr")]
                 (errors query) => (contains {:select anything})

                 (errors (assoc query :group "state_abbr, county"))
                 =not=> (contains {:select anything})))

         (fact "it errors if you reference a field that is not in the slice"
               (errors (q :select "foo"))
               => (contains {:select anything})

               (errors (q :select "state_abbr, SUM(foo)"
                          :group "state_abbr"))
               => (contains {:select anything})
               
               (errors (q :select "foo.population"))
               => (contains {:select anything}))
         
         (fact "it errors if it cannot parse GROUP"
               (errors (q :select "state_abbr"
                          :group "what what"))
               => (contains {:group anything}))      

         (fact "it errors if you use GROUP without SELECT"
               (let [query (q :group "state_abbr")]
                 (errors query) => (contains {:group anything})

                 (errors (assoc query :select "state_abbr"))
                 =not=> (contains {:group anything})))

         (fact "it errors if you GROUP on something that is not a dimension"
               (let [query (q :select "tax_returns"
                              :group "tax_returns")]
                 (errors query) => (contains {:group anything})))

         (fact "it errors if it cannot parse WHERE"
               (errors (q :where "what what"))
               => (contains {:where anything}))

         (fact "it errors if it cannot parse ORDER BY"
               (errors (q :orderBy "what what"))
               => (contains {:orderBy anything})

               (errors (q :orderBy "state_abbr DESC"))
               =not=> (contains {:orderBy anything}))
         
         (fact "it errors if limit is not an integer string"
               (errors (q :limit "ten"))
               => (contains {:limit anything})

               (errors (q :limit "10"))
               =not=> (contains {:limit anything}))

         (fact "it does not error if limit is greater than 1000"
               (errors (q :limit "1001"))
               =not=> (contains {:limit ["The maximum limit is 1000."]}))
         
         (fact "it errors if offset is not an integer string"
               (errors (q :offset "ten"))
               => (contains {:offset anything})

               (errors (q :offset "10"))
               =not=> (contains {:offset anything}))))
