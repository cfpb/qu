(ns cfpb.qu.test.where.parse-fns
  (:require [clojure.test :refer :all]
            [protoflex.parse :as p]
            [cfpb.qu.where.parse-fns
             :refer [value identifier function comparison where-expr]]))

(deftest parse-value-number
  (is (= (p/parse value "4.5")
         4.5)))

(deftest parse-value-numeric-expression
  (is (= (p/parse value "(3 + 4) * 2")
         14)))

(deftest parse-value-dq-str
  (is (= (p/parse value "\"hello world\"")
         "hello world")))

(deftest parse-value-sq-str
  (is (= (p/parse value "'hello world'")
         "hello world")))

(deftest parse-value-boolean-literals
  (are [x y] (= x y)
       {:bool true} (p/parse value "true")
       {:bool false} (p/parse value "false")))

(deftest parse-value-function
  (is (= (p/parse value "hello(world, 2)")
         {:function {:name :hello :args [:world 2]}})))

(deftest parse-nested-functions
  (is (= (p/parse function "hello(world, min(2, 3), 3)")
         {:function {:name :hello
                     :args [:world
                            {:function {:name :min
                                        :args [2 3]}}
                            3]}})))

(deftest parse-identifier
  (are [x y] (= x y)
       :hello (p/parse identifier "hello")
       :hello-world (p/parse identifier "hello-world")
       :HelloWorld (p/parse identifier "HelloWorld")
       :h3110_w0r1d (p/parse identifier "h3110_w0r1d")))

(deftest parse-comparison
  (are [x y] (= x y)

       {:comparison [:length :> 3]}
       (p/parse comparison "length > 3")

       ; Ensure this works without spaces.
       {:comparison [:length :> 3]}
       (p/parse comparison "length>3")

       {:comparison [:length :< 3]}
       (p/parse comparison "length < 3")

       {:comparison [:size :!= 12.5]}
       (p/parse comparison "size != 12.5")))

(deftest parse-where-expr
  (are [x y] (= x y)
       {:comparison [:length :> 3]}
       (p/parse where-expr "length > 3")

       {:not {:comparison [:length :> 3]}}
       (p/parse where-expr "NOT length > 3")

       {:left {:comparison [:length :> 3]}
        :op :AND
        :right {:comparison [:height :< 4.5]}}
       (p/parse where-expr "length > 1+2 AND height < 4.5")

       {:left {:comparison [:length :> 3]}
        :op :AND
        :right {:comparison [:height :< 4.5]}}
       (p/parse where-expr "(length > 3 AND height < 4.5)")

       {:not {:left {:comparison [:length :> 3]}
              :op :AND
              :right {:comparison [:height :< 4.5]}}}
       (p/parse where-expr "NOT (length > 3 AND height < 4.5)")
       
       {:left {:left {:comparison [:length :> 3]}
               :op :AND
               :right {:comparison [:height :< 4.5]}}
        :op :OR
        :right {:comparison [:name := "Pete"]}}
       (p/parse where-expr "length > 3 AND height < 4.5 OR name = \"Pete\"")

       {:left {:comparison [:length :> 3]}
        :op :AND
        :right {:left {:comparison [:height :< 4.5]}
                :op :OR
                :right {:comparison [:name := "Pete"]}}}
       (p/parse where-expr "length > 3 AND (height < 4.5 OR name = \"Pete\")")))
