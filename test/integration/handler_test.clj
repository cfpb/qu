(ns integration.handler-test
  (:require [midje.sweet :refer :all]
            [ring.mock.request :refer :all]
            [cfpb.qu.handler :refer [app]]
            [cfpb.qu.data :as data]
            [monger.core :as mongo]))

(with-state-changes [(before :facts (data/connect-mongo))
                     (after :facts (data/disconnect-mongo))]
  
  (fact "the index URL redirects to /data"
        (app (request :get "/"))
        => (contains {:status 302
                      :headers {"Location" "/data"}}))

  (facts "about /data"
       (fact "it returns successfully"
             (app (request :get "/data"))
             => (contains {:status 200
                           :headers {"Content-Type" "text/html;charset=UTF-8"}})

             (app (request :get "/data.xml"))
             => (contains {:status 200
                           :headers {"Content-Type" "application/xml;charset=UTF-8"}})))

  (facts "about /data/county_taxes"
         (fact "it returns successfully"
             (app (request :get "/data/integration_test"))
             => (contains {:status 200
                           :headers {"Content-Type" "text/html;charset=UTF-8"}})

             (app (request :get "/data/integration_test.xml"))
             => (contains {:status 200
                           :headers {"Content-Type" "application/xml;charset=UTF-8"}})))

  (facts "about /data/bad-dataset"
         (fact "it returns a 404"
               (app (request :get "/data/bad-dataset"))
               => (contains {:status 404
                             :headers {"Content-Type" "text/html"
                                       "Vary" "Accept"}})

               (app (request :get "/data/bad-dataset.xml"))
               => (contains {:status 404
                             :headers {"Content-Type" "application/xml;charset=UTF-8"}}))))

