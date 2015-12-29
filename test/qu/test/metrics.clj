(ns qu.test.metrics
  (:require [clojure.test :refer :all]
            [qu.metrics :as metrics]))

(deftest metrics-path
  (testing "it returns data.html when empty"
    (is (= (metrics/metrics-path "") "/data.html")))

  (testing "it adds html extension when no extension is present"
    (is (= (metrics/metrics-path "/honeybadger") "/honeybadger.html")))

  (testing "it uses extension when present"
    (are [x y] (= x y)
         (metrics/metrics-path "/honeybadger.json") "/honeybadger.json"
         (metrics/metrics-path "/honeybadger.csv") "/honeybadger.csv"
         (metrics/metrics-path "/honeybadger.xml") "/honeybadger.xml"
         (metrics/metrics-path "/honeybadger/dont/take/no.xml") "/honeybadger/dont/take/no.xml"
         (metrics/metrics-path "/honeybadger/dont/take/no") "/honeybadger/dont/take/no.html")))

;; (run-tests)
