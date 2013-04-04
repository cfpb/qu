(ns cfpb.qu.query.parser-test
  (:require [midje.sweet :refer :all]
            [protoflex.parse :as p]
            [cfpb.qu.query.parser
             :refer [value identifier function comparison predicate where-expr]]))

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

(facts "about functions"
       (fact "can be parsed"
             (p/parse function "hello(world)") =>
             {:function {:name :hello :args [:world]}})

       (fact "can have values or identifiers as arguments"
             (p/parse function "hello(world, 2)") =>
             {:function {:name :hello :args [:world 2]}}

             (p/parse function "hello(2+1)") =>
             {:function {:name :hello :args [3]}})

       (fact "spaces in the arglist are irrelevant"
             (p/parse function "hello(world,2)") =>
             {:function {:name :hello :args [:world 2]}}

             (p/parse function "hello(   world,        2    )") =>
             {:function {:name :hello :args [:world 2]}}))

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


(facts "about predicates"
       (fact "can be comparisons or functions"
             (p/parse predicate "length > 3") => {:comparison [:length :> 3]}
             (p/parse predicate "starts_with(name, 'Pete')") =>
             {:function {:name :starts_with
                         :args [:name "Pete"]}}))

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
