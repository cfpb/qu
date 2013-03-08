(ns cfpb.qu.test.resources
  (:require [clojure.test :refer :all]
            [cfpb.qu.resources :refer :all]))

(deftest parse-params-returns-string-clauses
  (let [slice-def {}
        params {"$select" "age,race"}]
    (is (= (:clauses (parse-params slice-def params))
           {:$select "age,race"}))))
