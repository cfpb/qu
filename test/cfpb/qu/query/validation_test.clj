(ns cfpb.qu.query.validation-test
  (:require [midje.sweet :refer :all]
            [cfpb.qu.query.validation :refer :all]))

(facts "about validate"
       (let [slicedef {:dimensions ["state_abbr" "county"]
                       :metrics ["tax_returns"]}
             errors (comp :errors validate)]

         (fact "it errors when it cannot parse SELECT"
               (errors {:select "what what" :slicedef slicedef}) =>
               (contains {:select anything}))

         (fact "it errors when you have an aggregation in SELECT without a GROUP"
               (let [query {:select "state_abbr, SUM(tax_returns)"
                            :slicedef slicedef}]
                 (errors query) => (contains {:select anything})

                 (errors (assoc query :group "state_abbr")) =not=>
                 (contains {:select anything})))

         (fact "it errors if you have an unaggregated SELECT field without it being in GROUP"
               (let [query {:select "state_abbr, county, SUM(tax_returns)"
                            :group "state_abbr"
                            :slicedef slicedef}]
                 (errors query) => (contains {:select anything})

                 (errors (assoc query :group "state_abbr, county"))
                 =not=> (contains {:select anything})))

         (fact "it does not error if you reference concept data on an existing field"
               (errors {:select "county.population" :slicedef slicedef})
               =not=> (contains {:select anything}))

         (fact "it errors if you reference a field that is not in the slice"
               (errors {:select "foo" :slicedef slicedef})
               => (contains {:select anything})

               (errors {:select "state_abbr, SUM(foo)"
                        :group "state_abbr"
                        :slicedef slicedef})
               => (contains {:select anything})
               
               (errors {:select "foo.population" :slicedef slicedef})
               => (contains {:select anything}))
         
         (fact "it errors if it cannot parse GROUP"
               (errors {:select "state_abbr"
                        :group "what what"
                        :slicedef slicedef})
               => (contains {:group anything}))

         (fact "it errors if you use GROUP without SELECT"
               (let [query {:group "state_abbr"
                            :slicedef slicedef}]
                 (errors query) => (contains {:group anything})

                 (errors (assoc query :select "state_abbr"))
                 =not=> (contains {:group anything})))

         (fact "it errors if you GROUP on something that is not a dimension"
               (let [query {:select "tax_returns"
                            :group "tax_returns"
                            :slicedef slicedef}]
                 (errors query) => (contains {:group anything})))

         (fact "it errors if it cannot parse WHERE"
               (errors {:where "what what" :slicedef slicedef})
               => (contains {:where anything}))

         (fact "it errors if it cannot parse ORDER BY"
               (errors {:orderBy "what what" :slicedef slicedef})
               => (contains {:orderBy anything})

               (errors {:orderBy "state_abbr DESC" :slicedef slicedef})
               =not=> (contains {:orderBy anything}))
         
         (fact "it errors if limit is not an integer string"
               (errors {:limit "ten" :slicedef slicedef})
               => (contains {:limit anything})

               (errors {:limit "10" :slicedef slicedef})
               =not=> (contains {:limit anything}))

         (fact "it errors if limit is greater than 1000"
               (errors {:limit "1001" :slicedef slicedef})
               => (contains {:limit ["The maximum limit is 1000."]}))         
         
         (fact "it errors if offset is not an integer string"
               (errors {:offset "ten" :slicedef slicedef})
               => (contains {:offset anything})

               (errors {:offset "10" :slicedef slicedef})
               =not=> (contains {:offset anything}))))
