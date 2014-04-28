(ns qu.test.query.where
  (:require [clojure.test :refer :all]
            [qu.test-util :refer :all]
            [protoflex.parse :as p]
            [qu.query.where :refer [parse mongo-eval]])
  (:import (java.util.regex Pattern)))

(deftest test-parse
  (testing "can parse simple comparisons"
    (does=
     (parse "length > 3") {:comparison [:length :> 3]}
     (parse "name IS NULL") {:comparison [:name := nil]}
     (parse "name IS NOT NULL") {:comparison [:name :!= nil]}))

  (testing "can parse complex comparisons"
    (does=
     (parse "length > 3 AND height < 4.5") 
     {:left {:comparison [:length :> 3]}
      :op :AND
      :right {:comparison [:height :< 4.5]}}

     (parse "length > 3 AND height < 4.5 OR name = \"Pete\"") 
     {:left {:left {:comparison [:length :> 3]}
             :op :AND
             :right {:comparison [:height :< 4.5]}}
      :op :OR
      :right {:comparison [:name := "Pete"]}}

     (parse "length > 3 AND (height < 4.5 OR name = \"Pete\")") 
     {:left {:comparison [:length :> 3]}
      :op :AND
      :right {:left {:comparison [:height :< 4.5]}
              :op :OR
              :right {:comparison [:name := "Pete"]}}})))

(deftest test-mongo-eval
  (testing "handles equality correctly"
    (is (= (mongo-eval (parse "length = 3")) {:length 3})))

  (testing "handles non-equality comparisons"
    (does=
     (mongo-eval (parse "length < 3")) {:length {:$lt 3}}
     (mongo-eval (parse "length >= 3")) {:length {:$gte 3}}))

  (testing "handles booleans in comparisons"
    (does=
     (mongo-eval (parse "exempt = TRUE")) {:exempt true}))

  (testing "handles LIKE comparisons"
    (does-re-match
     "Marc" (:name (mongo-eval (parse "name LIKE 'Mar%'")))
     "Markus" (:name (mongo-eval (parse "name LIKE 'Mar%'")))
     "Mar" (:name (mongo-eval (parse "name LIKE 'Mar%'")))
     "Clinton and Marc" (:name (mongo-eval (parse "name LIKE '%Mar%'")))
     "Mick" (:name (mongo-eval (parse "name LIKE 'M__k'")))
     "Mark" (:name (mongo-eval (parse "name LIKE 'M__k'")))
     ".M" (:name (mongo-eval (parse "name LIKE '._'"))))

    (does-not-re-match
     "CMark" (:name (mongo-eval (parse "name LIKE 'Mar%'")))
     "Mak" (:name (mongo-eval (parse "name LIKE 'M__k'")))
     "CM" (:name (mongo-eval (parse "name LIKE '._'")))))

  (testing "handles ILIKE comparisons"
    (does-re-match
     "Blob fish" (:name (mongo-eval (parse "name ILIKE 'blob%'")))
     "AYE AYE" (:name (mongo-eval (parse "name ILIKE 'aye%ay%'")))
     "jerboa ears" (:name (mongo-eval (parse "name ILIKE 'JERB%'")))
     "greater pangolin is great" (:name (mongo-eval (parse "name ILIKE '%P_ng_lin%'")))
     "D. melanogaster" (:name (mongo-eval (parse "name ILIKE 'D.%Melan%'"))))             

    (does-not-re-match
     "goeduck clam" (:name (mongo-eval (parse "name ILIKE 'GEODUCK clam'")))
     "geoduck clam" (:name (mongo-eval (parse "name ILIKE 'G._DUCK clam'")))))


  (testing "handles complex comparisons"
    (does=
     (mongo-eval (parse "length > 3 AND height = 4.5"))
     {:$and [ {:length {:$gt 3}} {:height 4.5}]}

     (mongo-eval (parse "length > 3 OR height = 4.5"))
     {:$or [{:length {:$gt 3}} {:height 4.5}]}

     (mongo-eval (parse "length > 3 OR height = 4.5 OR width < 2"))
     {:$or [{:length {:$gt 3}} {:height 4.5} {:width {:$lt 2}}]}

     (mongo-eval (parse "length > 3 OR height = 4.5 OR width < 2 OR name = 'Pete'"))
     {:$or [{:length {:$gt 3}} {:height 4.5} {:width {:$lt 2}} {:name "Pete"}]}

     (mongo-eval (parse "length > 3 AND (height < 4.5 OR name = \"Pete\" OR width < 2)"))
     {:$and [{:length {:$gt 3}}
              {:$or [{:height {:$lt 4.5}}
                      {:name "Pete"}
                      {:width {:$lt 2}}]}]}))

  (testing "handles IN comparisons"
    (does=
     (mongo-eval (parse "name IN (\"Pete\", \"Sam\")"))
     {:name {:$in ["Pete" "Sam"]}}))

  (testing "handles simple comparisons with NOT"
    (does=
     (mongo-eval (parse "NOT name = \"Pete\""))
     {:name {:$ne "Pete"}}

     (mongo-eval (parse "NOT name != \"Pete\""))
     {:name "Pete"}

     (mongo-eval (parse "NOT length < 3"))
     {:length {:$not {:$lt 3}}}))

  (testing "handles complex comparisons with NOT and AND"
    (does=
     (mongo-eval (parse "NOT (length > 3 AND height = 4.5)"))
     {:$or [{:length {:$not {:$gt 3}}} {:height {:$ne 4.5}}]}

     (mongo-eval (parse "NOT (length > 3 AND height = 4.5 AND width < 2)"))
     {:$or [{:length {:$not {:$gt 3}}} {:height {:$ne 4.5}} {:width {:$not {:$lt 2}}}]}))

  (testing "uses $nor on complex comparisons with NOT and OR"
    (does=
     (mongo-eval (parse "NOT (length > 3 OR height = 4.5)"))
     {:$nor [{:length {:$gt 3}} {:height 4.5}]}

     (mongo-eval (parse "NOT (length > 3 OR height = 4.5 OR width < 2)"))
     {:$nor [{:length {:$gt 3}} {:height 4.5} {:width {:$lt 2}}]}))

  (testing "NOT binds tighter than AND"
    (does=
     (mongo-eval (parse "NOT length > 3 AND height = 4.5"))
     {:$and [{:length {:$not {:$gt 3}}} {:height 4.5}]}))

  (testing "handles nested comparisons with multiple NOTs"
    (does=
     (mongo-eval (parse "NOT (length > 3 OR NOT (height > 4.5 AND name IS NOT NULL))"))
     {:$nor [{:length {:$gt 3}}
              {:$or [{:height {:$not {:$gt 4.5}}}
                      {:name nil}]}]}

     (mongo-eval (parse "NOT (length > 3 AND NOT (height > 4.5 AND name = \"Pete\"))"))
     {:$or [{:length {:$not {:$gt 3}}}
             {:$and [{:height {:$gt 4.5}}
                      {:name "Pete"}]}]})))


;; (run-tests)
