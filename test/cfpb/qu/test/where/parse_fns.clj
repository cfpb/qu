(ns cfpb.qu.test.where.parse-fns
  (:require [midje.sweet :refer :all]
            [protoflex.parse :as p]
            [cfpb.qu.where.parse-fns
             :refer [value identifier function comparison where-expr]]))

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
             (p/parse value "false") => {:bool false})

       (fact "can parse functions"
             (p/parse value "hello(world, 2)") =>
             {:function {:name :hello :args [:world 2]}}

             (p/parse function "hello(world, min(2, 3), 3)") =>
             {:function {:name :hello
                         :args [:world
                                {:function {:name :min
                                            :args [2 3]}}
                                3]}}))

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

       (fact "spaces are irrelevant"
             (p/parse comparison "length>3") => {:comparison [:length :> 3]}))

(facts "about where expressions"
       (fact "can be comparisons"
             (p/parse where-expr "length > 3") => {:comparison [:length :> 3]})

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
