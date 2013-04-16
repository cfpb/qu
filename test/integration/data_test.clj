(ns integration.data-test
  (:require [midje.sweet :refer :all]
            [cfpb.qu.data :refer :all]
            [cfpb.qu.query :refer [is-aggregation? params->Query]]))

(facts "get-find queries using with-collection"
      (fact "whoa"
            1 => 2))
