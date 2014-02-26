(ns qu.test.query.parser
  (:require [clojure.test :refer :all]
            [qu.test-util :refer :all]            
            [protoflex.parse :as p]
            [qu.query.parser :refer :all]
            [clj-time.core :as time]))

(defmacro has-parse-error [& body]
  `(try
     ~@body
     (is false)
     (catch Exception ex#
       (is (re-find #"^Parse Error" (.getMessage ex#))))))

(deftest test-value
  (testing "can parse numbers"
    (is (= (p/parse value "4.5"))) 4.5)

  (testing "can parse strings with single or double quotes"
    (is (= (p/parse value "\"hello world\"") "hello world"))
    (is (= (p/parse value "'hello world'") "hello world")))

  (testing "can parse boolean literals"
    (is (= (p/parse value "true") {:bool true}))
    (is (= (p/parse value "false") {:bool false})))

  (testing "can parse dates"
    (is (= (p/parse value "2013-04-01") (time/date-time 2013 4 1)))
    (is (= (p/parse value "1999/12/31") (time/date-time 1999 12 31)))))

(deftest test-identifiers
  (testing "identifiers can be made up of letters, numbers, dashes, and underscores"
    (does=
     (p/parse identifier "hello") :hello
     (p/parse identifier "hello-world") :hello-world
     (p/parse identifier "HelloWorld") :HelloWorld
     (p/parse identifier "h3110_w0r1d") :h3110_w0r1d))

  (testing "identifiers must start with a letter"
    (has-parse-error
      (p/parse identifier "3times"))))

(deftest test-comparisons
  (testing "simple comparisons can be parsed"
    (does=
     (p/parse comparison "length > 3") {:comparison [:length :> 3]}
     (p/parse comparison "length < 3") {:comparison [:length :< 3]}
     (p/parse comparison "size != 12.5") {:comparison [:size :!= 12.5]}))

  (testing "IS NULL and IS NOT NULL comparisons can be parsed"
    (does=
     (p/parse comparison "length IS NULL") 
     {:comparison [:length := nil]}
     
     (p/parse comparison "length IS NOT NULL") 
     {:comparison [:length :!= nil]}))
       
  (testing "LIKE and ILIKE comparisons can be parsed"
    (does=
     (p/parse comparison "name LIKE 'Mar%'") 
     {:comparison [:name :LIKE "Mar%"]}
     
     (p/parse comparison "name ILIKE 'mar%'") 
     {:comparison [:name :ILIKE "mar%"]}))

  (testing "IN comparisons can be parsed"
    (is (= (p/parse comparison "length IN (1, 2, 3)")
           {:comparison [:length :IN [1 2 3]]})))

  (testing "spaces are irrelevant"
    (is (= (p/parse comparison "length>3")
           {:comparison [:length :> 3]}))))


(deftest test-where-expressions
  (testing "can be comparisons"
    (does=
     (p/parse where-expr "length > 3") {:comparison [:length :> 3]}
     (p/parse where-expr "tax_returns > 20000") {:comparison [:tax_returns :> 20000]}))

  (testing "can have NOT operators"
    (does=
     (p/parse where-expr "NOT length > 3") {:not {:comparison [:length :> 3]}}
     (p/parse where-expr "NOT (length > 3 AND height < 4.5)") 
     {:not {:left {:comparison [:length :> 3]}
            :op :AND
            :right {:comparison [:height :< 4.5]}}}))

  (testing "can have AND and OR operators"
    (does=
     (p/parse where-expr "length > 3 AND height < 4.5") 
     {:left {:comparison [:length :> 3]}
      :op :AND
      :right {:comparison [:height :< 4.5]}}

     (p/parse where-expr "length > 3 AND height < 4.5 OR name = \"Pete\"") 
     {:left {:left {:comparison [:length :> 3]}
             :op :AND
             :right {:comparison [:height :< 4.5]}}
      :op :OR
      :right {:comparison [:name := "Pete"]}}))
  
  (testing "can parse a query with four parts"
    (does=
     (p/parse where-expr "as_of_year=2011 AND state_abbr=\"CA\" AND applicant_race_1=1 AND applicant_ethnicity=1") 
     {:left {:left {:left {:comparison [:as_of_year := 2011]}
                    :op :AND
                    :right {:comparison [:state_abbr := "CA"]}}
             :op :AND
             :right {:comparison [:applicant_race_1 := 1]}}
      :op :AND
      :right {:comparison [:applicant_ethnicity := 1]}}))
  
  (testing "can have parentheses for precedence"
    (does=
     (p/parse where-expr "(length > 3 AND height < 4.5)") 
     {:left {:comparison [:length :> 3]}
      :op :AND
      :right {:comparison [:height :< 4.5]}}
     
     (p/parse where-expr "length > 3 AND (height < 4.5 OR name = \"Pete\")") 
     {:left {:comparison [:length :> 3]}
      :op :AND
      :right {:left {:comparison [:height :< 4.5]}
              :op :OR
              :right {:comparison [:name := "Pete"]}}})))

(deftest test-select-expressions
  (testing "can have one column"
    (is (= (p/parse select-expr "length")) [{:select :length}]))
       
  (testing "can have multiple columns"
    (is (= (p/parse select-expr "length, height") 
           [{:select :length}
            {:select :height}])))

  (testing "can have aggregations"
    (is (= (p/parse select-expr "state, SUM(population)") 
           [{:select :state}
            {:aggregation [:SUM :population]
             :select :sum_population}])))

  (testing "COUNT aggregations do not need an identifier"
    (does=
     (p/parse select-expr "state, COUNT()") 
     [{:select :state}
      {:aggregation [:COUNT :_id]
       :select :count}]

     (p/parse select-expr "state, count()") 
     [{:select :state}
      {:aggregation [:COUNT :_id]
       :select :count}]))

  (testing "aggregations are case-insensitive"
    (does=
     (p/parse select-expr "state, sum(population)") 
     [{:select :state}
      {:aggregation [:SUM :population]
       :select :sum_population}]
     
     (p/parse select-expr "state, cOuNt(population)") 
     [{:select :state}
      {:aggregation [:COUNT :population]
       :select :count_population}]))

  (testing "invalid aggregations do not work"
    (has-parse-error
     (p/parse select-expr "state, TOTAL(population)"))))

(deftest test-group-expressions
  (testing "can have one column"
    (is (= (p/parse group-expr "state") [:state])))
       
  (testing "can have multiple columns"
    (is (= (p/parse group-expr "state, county") [:state :county]))))

;; (run-tests)
