(ns cfpb.qu.test.logging
  (:require [clojure.test :refer :all]
            [cfpb.qu.logging :as log]))

(deftest metrics-path
  (testing "it returns data.html when empty"
    (is (= (log/metrics-path "") "/data.html")))

  (testing "it adds html extension when no extension is present"
    (is (= (log/metrics-path "/honeybadger") "/honeybadger.html")))

  (testing "it uses extension when present"
    (are [x y] (= x y)
         (log/metrics-path "/honeybadger.json") "/honeybadger.json"
         (log/metrics-path "/honeybadger.csv") "/honeybadger.csv"
         (log/metrics-path "/honeybadger.xml") "/honeybadger.xml"
         (log/metrics-path "/honeybadger/dont/take/no.xml") "/honeybadger/dont/take/no.xml"
         (log/metrics-path "/honeybadger/dont/take/no") "/honeybadger/dont/take/no.html")))

;; (run-tests)
