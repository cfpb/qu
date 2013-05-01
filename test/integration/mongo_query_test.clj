(ns integration.mongo-query-test
  (:require [midje.sweet :refer :all]
            [cfpb.qu.data :as data]
            [cfpb.qu.loader :as loader]
            [monger.core :as mongo]))


(with-state-changes [(before :facts (do
                                      (data/connect-mongo)
                                      (loader/load-dataset "integration_test")))
                     (after :facts (data/disconnect-mongo))]






  (fact [1 2 3 4 5] => (contains [1 2])))
