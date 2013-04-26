(ns cfpb.qu.handler-test
  (:require [midje.sweet :refer :all]
            [ring.mock.request :refer :all]
            [cfpb.qu.handler :refer [app]]))

(fact "the index URL redirects to /data"
      (app (request :get "/"))
      => (contains {:status 302
                    :headers {"Location" "/data"}}))

(facts "about /data"
       (prerequisite (#'cfpb.qu.data/get-datasets) => [])
       
       (fact "it returns successfully"
             (app (request :get "/data"))
             => (contains {:status 200
                           :headers {"Content-Type" "text/html;charset=UTF-8"}})

             (app (request :get "/data.xml"))
             => (contains {:status 200
                           :headers {"Content-Type" "application/xml;charset=UTF-8"}})))

