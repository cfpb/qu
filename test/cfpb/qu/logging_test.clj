(ns cfpb.qu.logging-test
  (:require [midje.sweet :refer :all]
            [cfpb.qu.logging :as log]))

(facts "about metrics-path"
  (fact "it returns data.html when empty"
    (log/metrics-path "") => "/data.html")

  (fact "it adds html extension when no extension is present"
    (log/metrics-path "/honeybadger") => "/honeybadger.html")

  (fact "it uses extension when present"
    (log/metrics-path "/honeybadger.json") => "/honeybadger.json"
    (log/metrics-path "/honeybadger.csv") => "/honeybadger.csv"
    (log/metrics-path "/honeybadger.xml") => "/honeybadger.xml"
    (log/metrics-path "/honeybadger/dont/take/no.xml") => "/honeybadger/dont/take/no.xml"
    (log/metrics-path "/honeybadger/dont/take/no") => "/honeybadger/dont/take/no.html")
  )
