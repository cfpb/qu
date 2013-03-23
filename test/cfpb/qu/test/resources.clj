(ns cfpb.qu.test.resources
  (:require [midje.sweet :refer :all]
            [cfpb.qu.resources :refer :all]))

(fact "parse-params returns string clauses"
      (let [slice-def {}
            params {:$select "age,race"}]
        (:clauses (parse-params slice-def params)) => {:$select "age,race"}))
