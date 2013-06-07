(ns integration.slice-test
  (:require [midje.sweet :refer :all]
            [ring.mock.request :refer :all]
            [cfpb.qu.handler :refer [app]]
            [cfpb.qu.data :as data]))

(with-state-changes [(before :facts (data/connect-mongo))
                     (after :facts (data/disconnect-mongo))]

  (facts "about querying a slice with no parameters"
       (fact "it returns successfully as text/html"
             (app (request :get "/data/integration_test/incomes"))
             => (contains {:status 200
                           :headers {"Content-Type" "text/html;charset=UTF-8"
                                     "Vary" "Accept"}})))

  (facts "about querying a slice that does not exist"
         (fact "it returns a 404"
               (app (request :get "/data/bad-dataset/bad-slice"))
               => (contains {:status 404})

               ;; TODO why is this Content-Type different
               (app (request :get "/data/bad-dataset/bad-slice.xml"))
               => (contains {:status 404
                             :headers {"Content-Type" "application/xml;charset=UTF-8"
                                       "Vary" "Accept"}})))

  (facts "about specifying JSON"
         (fact "it returns a content-type of application/json"
             (app (request :get "/data/integration_test/incomes.json"))
             => (contains {:status 200
                           :headers {"Content-Type" "application/json;charset=UTF-8"
                                     "Vary" "Accept"}})))

  (facts "about specifying JSONP"
         (fact "it uses the callback we supply"
             (let [result (app (request :get "/data/integration_test/incomes.jsonp?$callback=foo"))]
               result => (contains {:status 200
                                    :headers {"Content-Type" "text/javascript;charset=UTF-8"
                                              "Vary" "Accept"}})

               (:body result) => #"^foo\("))

         (fact "it uses 'callback' by default"
               (let [result (app (request :get "/data/integration_test/incomes.jsonp"))]
                 result => (contains {:status 200
                                      :headers {"Content-Type" "text/javascript;charset=UTF-8"
                                                "Vary" "Accept"}})
                 
                 (:body result) => #"^callback\(")))
  
  (facts "about specifying XML"
         (fact "it returns a content-type of application/xml"
             (app (request :get "/data/integration_test/incomes.xml"))
             => (contains {:status 200
                           :headers {"Content-Type" "application/xml;charset=UTF-8"
                                     "Vary" "Accept"}})))

  (facts "about querying with an error"
         (fact "it returns the status code for bad request"
               (app (request :get "/data/integration_test/incomes?$where=peanut%20butter"))
               => (contains {:status 400}))))
