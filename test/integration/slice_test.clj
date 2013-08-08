(ns integration.slice-test
  (:require [midje.sweet :refer :all]
            [ring.mock.request :refer :all]
            [cfpb.qu.handler :refer [app]]
            [cfpb.qu.loader :as loader]
            [cfpb.qu.data :as data]
            [cfpb.qu.test-util :refer :all]))

(with-server
  (with-state-changes [(before :facts (do (data/connect-mongo)
                                        (loader/load-dataset "integration_test")))
                       (after :facts (data/disconnect-mongo))]
    (facts "about querying a slice with no parameters"
           (fact "it returns successfully as text/html"
                 (let [resp (GET "/data/integration_test/slice/incomes")]
                   (:status resp) => 200
                   (:headers resp) => (contains {:content-type "text/html;charset=UTF-8"
                                                 :vary "Accept"}))))

    (facts "about querying a slice that does not exist"
           (fact "it returns a 404"
                 (let [resp (GET "/data/bad-dataset/slice/bad-slice")]
                   (:status resp) => 404)

                 (let [resp (GET "/data/bad-dataset/slice/bad-slice.xml")]
                   (:status resp) => 404
                   (:headers resp) => (contains {:content-type "application/xml;charset=UTF-8"
                                                 :vary "Accept"}))))

    (facts "about specifying JSON"
           (fact "it returns a content-type of application/json"
                 (let [resp (GET "/data/integration_test/slice/incomes.json")]
                   (:status resp) => 200
                   (:headers resp) => (contains {:content-type "application/json;charset=UTF-8"}))))

    (facts "about specifying JSONP"
           (fact "it uses the callback we supply"
                 (let [resp (GET "/data/integration_test/slice/incomes.jsonp?$callback=foo")]
                   (:status resp) => 200
                   (:headers resp) => (contains {:content-type "text/javascript;charset=UTF-8"})
                   (:body resp) => #"^foo\("))

           (fact "it uses 'callback' by default"
                 (let [resp (GET "/data/integration_test/slice/incomes.jsonp")]
                   (:status resp) => 200
                   (:headers resp) => (contains {:content-type "text/javascript;charset=UTF-8"})
                   (:body resp) => #"^callback\(")))
    
    (facts "about specifying XML"
           (fact "it returns a content-type of application/xml"
                 (let [resp (GET "/data/integration_test/slice/incomes.xml")]
                   (:status resp) => 200
                   (:headers resp) => (contains {:content-type "application/xml;charset=UTF-8"}))))

    (facts "about querying with an error"
           (fact "it returns the status code for bad request"
                 (let [resp (GET "/data/integration_test/slice/incomes?$where=peanut%20butter")]
                   (:status resp) => 400)))))
