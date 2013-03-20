(ns cfpb.qu.test.where
  (:require [midje.sweet :refer :all]
            [protoflex.parse :as p]
            [cfpb.qu.where :refer [parse mongo-eval]]))

(facts "about parse"
       (fact "can parse simple comparisons"
             (parse "length > 3") => {:comparison [:length :> 3]}
             (parse "name IS NULL") => {:comparison [:name := nil]}
             (parse "name IS NOT NULL") => {:comparison [:name :!= nil]})

       (fact "can parse complex comparisons"
             (parse "length > 3 AND height < 4.5") =>
             {:left {:comparison [:length :> 3]}
              :op :AND
              :right {:comparison [:height :< 4.5]}}

             (parse "length > 3 AND height < 4.5 OR name = \"Pete\"") =>
             {:left {:left {:comparison [:length :> 3]}
                     :op :AND
                     :right {:comparison [:height :< 4.5]}}
              :op :OR
              :right {:comparison [:name := "Pete"]}}

             (parse "length > 3 AND (height < 4.5 OR name = \"Pete\")") =>
             {:left {:comparison [:length :> 3]}
              :op :AND
              :right {:left {:comparison [:height :< 4.5]}
                      :op :OR
                      :right {:comparison [:name := "Pete"]}}}))

(facts "about mongo-eval"
       (fact "handles equality correctly"
             (mongo-eval (parse "length = 3")) => {:length 3})

       (fact "handles non-equality comparisons"
             (mongo-eval (parse "length < 3")) => {:length {"$lt" 3}}
             (mongo-eval (parse "length >= 3")) => {:length {"$gte" 3}})

       (fact "handles complex comparisons"
             (mongo-eval (parse "length > 3 AND height = 4.5")) =>
             {"$and" [ {:length {"$gt" 3}} {:height 4.5}]}

             (mongo-eval (parse "length > 3 OR height = 4.5")) =>
             {"$or" [{:length {"$gt" 3}} {:height 4.5}]}

             (mongo-eval (parse "length > 3 AND (height < 4.5 OR name = \"Pete\")")) =>
             {"$and" [{:length {"$gt" 3}}
                      {"$or" [{:height {"$lt" 4.5}}
                              {:name "Pete"}]}]}
             )

       (fact "handles simplex comparisons with NOT"
             (mongo-eval (parse "NOT name = \"Pete\"")) =>
             {:name {"$ne" "Pete"}}

             (mongo-eval (parse "NOT name != \"Pete\"")) =>
             {:name "Pete"}

             (mongo-eval (parse "NOT length < 3")) =>
             {:length {"$gte" 3}})

       (fact "handles complex comparisons with NOT and AND"
             (mongo-eval (parse "NOT (length > 3 AND height = 4.5)")) =>
             {"$or" [{:length {"$lte" 3}} {:height {"$ne" 4.5}}]})

       (fact "uses $nor on complex comparisons with NOT and OR"
             (mongo-eval (parse "NOT (length > 3 OR height = 4.5)")) =>
             {"$nor" [{:length {"$gt" 3}} {:height 4.5}]})

       (fact "NOT binds tighter than AND"
             (mongo-eval (parse "NOT length > 3 AND height = 4.5")) =>
             {"$and" [{:length {"$lte" 3}} {:height 4.5}]})

       (fact "handles nested comparisons with multiple NOTs"
             (mongo-eval (parse "NOT (length > 3 OR NOT (height > 4.5 AND name IS NOT NULL))")) =>
             {"$nor" [{:length {"$gt" 3}}
                      {"$or" [{:height {"$lte" 4.5}}
                              {:name nil}]}]}

             (mongo-eval (parse "NOT (length > 3 AND NOT (height > 4.5 AND name = \"Pete\"))"))
             {"$or" [{:length {"$lte" 3}}
                     {"$and" [{:height {"$gt" 4.5}}
                              {:name "Pete"}]}]}))


