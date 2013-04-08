(ns cfpb.qu.query.parser-test
  (:require [midje.sweet :refer :all]
            [protoflex.parse :as p]
            [cfpb.qu.query.parser :refer :all]))

(facts "about value"
       (fact "can parse numbers"
             (p/parse value "4.5") => 4.5)

       (fact "can parse numeric expressions"
             (p/parse value "(3 + 4) * 2") => 14)

       (fact "can parse strings with single or double quotes"
             (p/parse value "\"hello world\"") => "hello world"
             (p/parse value "'hello world'") => "hello world")

       (fact "can parse boolean literals"
             (p/parse value "true") => {:bool true}
             (p/parse value "false") => {:bool false}))

(facts "about identifiers"
       (fact "identifiers can be made up of letters, numbers, dashes, and underscores"
             (p/parse identifier "hello") => :hello
             (p/parse identifier "hello-world") => :hello-world
             (p/parse identifier "HelloWorld") => :HelloWorld
             (p/parse identifier "h3110_w0r1d") => :h3110_w0r1d)

       (fact "identifiers must start with a letter"
             (p/parse identifier "3times") => (throws Exception #"^Parse Error")))

(facts "about comparisons"
       (fact "simple comparisons can be parsed"
             (p/parse comparison "length > 3") => {:comparison [:length :> 3]}
             (p/parse comparison "length < 3") => {:comparison [:length :< 3]}
             (p/parse comparison "size != 12.5") => {:comparison [:size :!= 12.5]})

       (fact "IS NULL and IS NOT NULL comparisons can be parsed"
             (p/parse comparison "length IS NULL") =>
             {:comparison [:length := nil]}

             (p/parse comparison "length IS NOT NULL") =>
             {:comparison [:length :!= nil]})
       
       (fact "LIKE and ILIKE comparisons can be parsed"
             (p/parse comparison "name LIKE 'Mar%'") =>
             {:comparison [:name :LIKE "Mar%"]}
             
             (p/parse comparison "name ILIKE 'mar%'") =>
             {:comparison [:name :ILIKE "mar%"]})

       (fact "spaces are irrelevant"
             (p/parse comparison "length>3") => {:comparison [:length :> 3]}))


(facts "about where expressions"
       (fact "can be comparisons"
             (p/parse where-expr "length > 3") => {:comparison [:length :> 3]}
             (p/parse where-expr "tax_returns > 20000") => {:comparison [:tax_returns :> 20000]})

       (fact "can have NOT operators"
             (p/parse where-expr "NOT length > 3") => {:not {:comparison [:length :> 3]}}
             (p/parse where-expr "NOT (length > 3 AND height < 4.5)") =>
             {:not {:left {:comparison [:length :> 3]}
                    :op :AND
                    :right {:comparison [:height :< 4.5]}}})

       (fact "can have AND and OR operators"
             (p/parse where-expr "length > 1+2 AND height < 4.5") =>
             {:left {:comparison [:length :> 3]}
              :op :AND
              :right {:comparison [:height :< 4.5]}}

             (p/parse where-expr "length > 3 AND height < 4.5 OR name = \"Pete\"") =>
             {:left {:left {:comparison [:length :> 3]}
                     :op :AND
                     :right {:comparison [:height :< 4.5]}}
              :op :OR
              :right {:comparison [:name := "Pete"]}})

       (fact "can have parentheses for precedence"
             (p/parse where-expr "(length > 3 AND height < 4.5)") =>
             {:left {:comparison [:length :> 3]}
              :op :AND
              :right {:comparison [:height :< 4.5]}}

             (p/parse where-expr "length > 3 AND (height < 4.5 OR name = \"Pete\")") =>
             {:left {:comparison [:length :> 3]}
              :op :AND
              :right {:left {:comparison [:height :< 4.5]}
                      :op :OR
                      :right {:comparison [:name := "Pete"]}}}))

(facts "about select expressions"
       (fact "can have one column"
             (p/parse select-expr "length") => [{:select :length}])
       
       (fact "can have multiple columns"
             (p/parse select-expr "length, height") =>
             [{:select :length}
              {:select :height}])

       (fact "can have aggregations"
             (p/parse select-expr "state, SUM(population)") =>
             [{:select :state}
              {:aggregation [:SUM :population]
               :select :sum_population}])

       (fact "invalid aggregations do not work"
             (p/parse select-expr "state, TOTAL(population)") =>
             (throws Exception #"^Parse Error")))

(facts "about select expressions"
       (fact "can have one column"
             (p/parse group-expr "state") => [:state])
       
       (fact "can have multiple columns"
             (p/parse group-expr "state, county") =>
             [:state :county]))
