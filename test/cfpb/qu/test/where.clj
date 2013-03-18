(ns cfpb.qu.test.where
  (:require [clojure.test :refer :all]
            [protoflex.parse :as p]
            [cfpb.qu.where :refer :all]))

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

(deftest parse-comp-op
  (are [x y] (= x y)
       :< (p/parse comp-op "<")
       :> (p/parse comp-op ">")
       :!= (p/parse comp-op "!=")))

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

       {:comparison [:length :< 3]}
       (p/parse comparison "length < 3")

       {:comparison [:size :!= 12.5]}
       (p/parse comparison "size != 12.5")))

(deftest parse-boolean-factor
  (are [x y] (= x y)

       {:comparison [:length :> 3]}
       (p/parse boolean-factor "length > 3")

       {:not {:comparison [:length :> 3]}}
       (p/parse boolean-factor "NOT length > 3")

       {:left {:comparison [:length :> 3]}
        :op :AND
        :right {:comparison [:height :< 4.5]}}
       (p/parse boolean-factor "(length > 3 AND height < 4.5)")

       {:not {:left {:comparison [:length :> 3]}
              :op :AND
              :right {:comparison [:height :< 4.5]}}}
       (p/parse boolean-factor "NOT (length > 3 AND height < 4.5)")))

(deftest parse-where-expr
  (are [x y] (= x y)
       {:comparison [:length :> 3]}
       (p/parse where-expr "length > 3")

       {:left {:comparison [:length :> 3]}
        :op :AND
        :right {:comparison [:height :< 4.5]}}
       (p/parse where-expr "length > 3 AND height < 4.5")

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

(deftest parse-simple-comparison
  (is (= {:comparison [:length :> 3]}
         (parse "length > 3"))))

(deftest parse-double-comparison
  (is (= {:left {:comparison [:length :> 3]}
          :op :AND
          :right {:comparison [:height :< 4.5]}}
         (parse "length > 3 AND height < 4.5"))))

(deftest parse-triple-comparison
  (is (= {:left {:left {:comparison [:length :> 3]}
                 :op :AND
                 :right {:comparison [:height :< 4.5]}}
          :op :OR
          :right {:comparison [:name := "Pete"]}}
         (parse "length > 3 AND height < 4.5 OR name = \"Pete\""))))

(deftest parse-nested-comparison
  (is (= {:left {:comparison [:length :> 3]}
          :op :AND
          :right {:left {:comparison [:height :< 4.5]}
                  :op :OR
                  :right {:comparison [:name := "Pete"]}}}
         (parse "length > 3 AND (height < 4.5 OR name = \"Pete\")"))))

(deftest mongo-eval-equality
  (is (= (mongo-eval (parse "length = 3"))
         {:length 3})))

(deftest mongo-eval-comparison
  (are [x y] (= x y)
       
       {:length {"$lt" 3}}
       (mongo-eval (parse "length < 3"))

       {:length {"$gte" 3}}
       (mongo-eval (parse "length >= 3"))))

(deftest mongo-eval-and
  (is (=
       {"$and" [ {:length {"$gt" 3}} {:height 4.5}]}
       (mongo-eval (parse "length > 3 AND height = 4.5")))))

(deftest mongo-eval-or
  (is (=
       {"$or" [{:length {"$gt" 3}} {:height 4.5}]}
       (mongo-eval (parse "length > 3 OR height = 4.5")))))

(deftest mongo-eval-parentheses
  (is (=
       {"$and" [{:length {"$gt" 3}}
                {"$or" [{:height {"$lt" 4.5}}
                        {:name "Pete"}]}]}
       (mongo-eval
        (parse "length > 3 AND (height < 4.5 OR name = \"Pete\")")))))
