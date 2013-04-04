(ns cfpb.qu.query.where-test
  (:require [midje.sweet :refer :all]
            [protoflex.parse :as p]
            [cfpb.qu.query.where :refer [parse mongo-eval mongo-fn]])
  (:import (java.util.regex Pattern)))

(defn has-ex-data [& data]
  (let [data (apply hash-map data)]
    (throws Exception
            (fn [ex] (every? (fn [[k v]]
                               (= (get (ex-data ex) k)
                                  v)) data)))))

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
                      :right {:comparison [:name := "Pete"]}}})

       (fact "returns an error when attempting to parse the unparsable"
             (parse "length <==> 3") => {:error true}))

(facts "about mongo-fn"
       (fact "handles the starts_with function"
             (-> (mongo-fn :starts_with [:name, "Ambrose"])
                 :name
                 str) => "^\\QAmbrose\\E")

       (fact "handles the contains function"
             (-> (mongo-fn :contains [:name, "Ambrose"])
                 :name
                 str) => "\\QAmbrose\\E")      

       (fact "handles no other functions"
             (mongo-fn :hello [:world]) => (has-ex-data :function :hello :args [:world])))

(facts "about mongo-eval"
       (fact "handles equality correctly"
             (mongo-eval (parse "length = 3")) => {:length 3})

       (fact "handles non-equality comparisons"
             (mongo-eval (parse "length < 3")) => {:length {"$lt" 3}}
             (mongo-eval (parse "length >= 3")) => {:length {"$gte" 3}})

       (fact "handles LIKE comparisons"
             "Marc" => (-> (mongo-eval (parse "name LIKE 'Mar%'"))
                           :name)
             "Markus" => (-> (mongo-eval (parse "name LIKE 'Mar%'"))
                             :name)
             "Mar" => (-> (mongo-eval (parse "name LIKE 'Mar%'"))
                           :name)
             "CMark" =not=> (-> (mongo-eval (parse "name LIKE 'Mar%'"))
                                :name)
             "Clinton and Marc" => (-> (mongo-eval (parse "name LIKE '%Mar%'"))
                                :name)

             "Mick" => (-> (mongo-eval (parse "name LIKE 'M__k'"))
                           :name)
             "Mark" => (-> (mongo-eval (parse "name LIKE 'M__k'"))
                           :name)
             "Mak" =not=> (-> (mongo-eval (parse "name LIKE 'M__k'"))
                           :name)

             ".M" => (-> (mongo-eval (parse "name LIKE '._'"))
                         :name)
             "CM" =not=> (-> (mongo-eval (parse "name LIKE '._'"))
                             :name))

       (fact "handles ILIKE comparisons"

             "Blob fish" => (-> (mongo-eval (parse "name ILIKE 'blob%'"))
                         :name)
             "AYE AYE" => (-> (mongo-eval (parse "name ILIKE 'aye%ay%'"))
                         :name)
             "jerboa ears" => (-> (mongo-eval (parse "name ILIKE 'JERB%'"))
                         :name)
             "greater pangolin is great" => (-> (mongo-eval (parse "name ILIKE '%P_ng_lin%'"))
                         :name)

             "goeduck clam" =not=> ( -> (mongo-eval (parse "name ILIKE 'GEODUCK clam'"))
                          :name)
             "geoduck clam" =not=> ( -> (mongo-eval (parse "name ILIKE 'G._DUCK clam'"))
                           :name)
             "D. melanogaster" => ( -> (mongo-eval (parse "name ILIKE 'D.%Melan%'"))
                           :name))


       (fact "handles complex comparisons"
             (mongo-eval (parse "length > 3 AND height = 4.5")) =>
             {"$and" [ {:length {"$gt" 3}} {:height 4.5}]}

             (mongo-eval (parse "length > 3 OR height = 4.5")) =>
             {"$or" [{:length {"$gt" 3}} {:height 4.5}]}

             (mongo-eval (parse "length > 3 AND (height < 4.5 OR name = \"Pete\")")) =>
             {"$and" [{:length {"$gt" 3}}
                      {"$or" [{:height {"$lt" 4.5}}
                              {:name "Pete"}]}]})

       (fact "handles simple comparisons with NOT"
             (mongo-eval (parse "NOT name = \"Pete\"")) =>
             {:name {"$ne" "Pete"}}

             (mongo-eval (parse "NOT name != \"Pete\"")) =>
             {:name "Pete"}

             (mongo-eval (parse "NOT length < 3")) =>
             {:length {"$not" {"$lt" 3}}})

       (fact "handles complex comparisons with NOT and AND"
             (mongo-eval (parse "NOT (length > 3 AND height = 4.5)")) =>
             {"$or" [{:length {"$not" {"$gt" 3}}} {:height {"$ne" 4.5}}]})

       (fact "uses $nor on complex comparisons with NOT and OR"
             (mongo-eval (parse "NOT (length > 3 OR height = 4.5)")) =>
             {"$nor" [{:length {"$gt" 3}} {:height 4.5}]})

       (fact "NOT binds tighter than AND"
             (mongo-eval (parse "NOT length > 3 AND height = 4.5")) =>
             {"$and" [{:length {"$not" {"$gt" 3}}} {:height 4.5}]})

       (fact "handles nested comparisons with multiple NOTs"
             (mongo-eval (parse "NOT (length > 3 OR NOT (height > 4.5 AND name IS NOT NULL))")) =>
             {"$nor" [{:length {"$gt" 3}}
                      {"$or" [{:height {"$not" {"$gt" 4.5}}}
                              {:name nil}]}]}

             (mongo-eval (parse "NOT (length > 3 AND NOT (height > 4.5 AND name = \"Pete\"))")) =>
             {"$or" [{:length {"$not" {"$gt" 3}}}
                     {"$and" [{:height {"$gt" 4.5}}
                              {:name "Pete"}]}]})

       (fact "handles the starts_with function"
             (-> (mongo-eval (parse "starts_with(name, 'Ambrose')"))
                 :name
                 str) => "^\\QAmbrose\\E")

       (fact "handles the contains function"
             (-> (mongo-eval (parse "contains(name, 'Ambrose')"))
                 :name
                 str) => "\\QAmbrose\\E")

       (fact "handles no other functions"
             (mongo-eval (parse "hello(world)")) =>
             (has-ex-data :function :hello :args [:world]))

       (fact "handles NOT and a function"
             (-> (mongo-eval (parse "NOT starts_with(name, 'Ambrose')"))
                 (get-in [:name "$not"])
                 str) => "^\\QAmbrose\\E"))


