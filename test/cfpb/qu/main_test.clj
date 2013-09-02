(ns cfpb.qu.main-test
  (:require [midje.sweet :refer :all]
            [ring.mock.request :refer :all]
            [cfpb.qu.main :refer [app]]
            [cfpb.qu.test-util :refer :all]))

(fact "the index URL redirects to /data"
      (app (request :get "/"))
      => (contains {:status 302
                    :headers {"Location" "/data"}}))

(facts "about /data"
       (prerequisite (#'cfpb.qu.data/get-datasets) => [])

       (fact "it returns successfully"
             (let [resp (app (request :get "/data"))]
               (:status resp) => 200
               (:headers resp) => (contains {"Content-Type" "text/html;charset=UTF-8"}))

             (let [resp (app (request :get "/data.xml"))]
               (:status resp) => 200
               (:headers resp) => (contains {"Content-Type" "application/xml;charset=UTF-8"}))))

(facts "about /data/:dataset"
       (fact "it returns successfully when the dataset exists"
             (prerequisite (#'cfpb.qu.data/get-metadata "good-dataset") => {})

             (let [resp (app (request :get "/data/good-dataset"))]
               (:status resp) => 200
               (:headers resp) => (contains {"Content-Type" "text/html;charset=UTF-8"}))

             (let [resp (app (request :get "/data/good-dataset.xml"))]
               (:status resp) => 200
               (:headers resp) => (contains {"Content-Type" "application/xml;charset=UTF-8"})))

       (fact "it returns a 404 when the dataset does not exist"
             (prerequisite (#'cfpb.qu.data/get-metadata "bad-dataset") => nil)

             (app (request :get "/data/bad-dataset"))
             => (contains {:status 404})))


(with-server
  (facts "about /data/:dataset/slice/:slice"       
         (fact "it returns successfully when the dataset and slice exist"
               (prerequisite (#'cfpb.qu.data/get-metadata "good-dataset") => {:slices {:whoa {}}}
                             (#'cfpb.qu.query/execute anything)
                             => {:total 0 :size 0 :data []})
               
               (let [resp (GET "/data/good-dataset/slice/whoa")]
                 (:status resp) => 200
                 (:headers resp) => (contains {:content-type "text/html;charset=UTF-8"}))
               
               (let [resp (GET "/data/good-dataset/slice/whoa.xml")]
                 (:status resp) => 200
                 (:headers resp) => (contains {:content-type "application/xml;charset=UTF-8"})))
         
         (fact "it returns a 404 when the dataset does not exist"
               (prerequisite (#'cfpb.qu.data/get-metadata "bad-dataset") => nil)
               
               (let [resp (GET "/data/bad-dataset/what")]
                 (:status resp) => 404))
         
         (fact "it returns a 404 when the slice does not exist"
               (prerequisite (#'cfpb.qu.data/get-metadata "good-dataset") => {:slices {}})
               
               (let [resp (GET "/data/good-dataset/what")]
                 (:status resp) => 404))))
