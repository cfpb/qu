(ns integration.main-test
  (:require [midje.sweet :refer :all]
            [ring.mock.request :refer :all]
            [cfpb.qu.main :refer [app]]
            [cfpb.qu.loader :as loader]
            [cfpb.qu.data :as data]
            [monger.core :as mongo]))

(with-state-changes [(before :facts (do (data/connect-mongo)
                                        (loader/load-dataset "integration_test")))
                     (after :facts (data/disconnect-mongo))]
  
  (fact "the index URL redirects to /data"
        (app (request :get "/"))
        => (contains {:status 302})

        (:headers (app (request :get "/")))
        => (contains {"Location" "/data"}))

  (facts "about /data"
       (fact "it returns successfully"
             (let [resp (app (request :get "/data"))]
               (:status resp) => 200
               (:headers resp) => (contains {"Content-Type" "text/html;charset=UTF-8"}))

             (let [resp (app (request :get "/data.xml"))]
               (:status resp) => 200
               (:headers resp) => (contains {"Content-Type" "application/xml;charset=UTF-8"}))))

  (facts "about /data/integration_test"
         (fact "it returns successfully"
               (let [resp (app (request :get "/data/integration_test"))]
                 (:status resp) => 200
                 (:headers resp) => (contains {"Content-Type" "text/html;charset=UTF-8"}))
               
               (let [resp (app (request :get "/data/integration_test.xml"))]
                 (:status resp) => 200
                 (:headers resp) => (contains {"Content-Type" "application/xml;charset=UTF-8"}))))
  
  (facts "about /data/bad-dataset"
         (fact "it returns a 404"
               (let [resp (app (request :get "/data/bad_dataset"))]
                 (:status resp) => 404
                 (:headers resp) => (contains {"Content-Type" "text/html"}))

               (let [resp (app (request :get "/data/bad_dataset.xml"))]
                 (:status resp) => 404
                 (:headers resp) => (contains {"Content-Type" "application/xml;charset=UTF-8"})))))

