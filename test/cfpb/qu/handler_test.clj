(ns cfpb.qu.handler-test
  (:require [midje.sweet :refer :all]
            [ring.mock.request :refer :all]
            [org.httpkit.server :refer [run-server]]
            [org.httpkit.client :as client]
            [cfpb.qu.handler :refer [app]]))

(def ^:dynamic *port* 4949)
(def server (atom nil))

(defn GET
  [path]
  @(client/get (str "http://localhost:" *port* path)))

(fact "the index URL redirects to /data"
      (app (request :get "/"))
      => (contains {:status 302
                    :headers {"Location" "/data"}}))

(facts "about /data"
       (prerequisite (#'cfpb.qu.data/get-datasets) => [])

       (fact "it returns successfully"
             (app (request :get "/data"))
             => (contains {:status 200
                           :headers {"Content-Type" "text/html;charset=UTF-8"
                                     "Vary" "Accept"}})

             (app (request :get "/data.xml"))
             => (contains {:status 200
                           :headers {"Content-Type" "application/xml;charset=UTF-8"
                                     "Vary" "Accept"}})))

(facts "about /data/:dataset"
       (fact "it returns successfully when the dataset exists"
             (prerequisite (#'cfpb.qu.data/get-metadata "good-dataset") => {})

             (app (request :get "/data/good-dataset"))
             => (contains {:status 200
                           :headers {"Content-Type" "text/html;charset=UTF-8"
                                     "Vary" "Accept"}})

             (app (request :get "/data/good-dataset.xml"))
             => (contains {:status 200
                           :headers {"Content-Type" "application/xml;charset=UTF-8"
                                     "Vary" "Accept"}}))

       (fact "it returns a 404 when the dataset does not exist"
             (prerequisite (#'cfpb.qu.data/get-metadata "bad-dataset") => nil)

             (app (request :get "/data/bad-dataset"))
             => (contains {:status 404})))


(with-state-changes [(before :facts (do (when @server (@server))
                                               (reset! server (run-server app {:port *port*}))))
                            (after :facts (let [stop-server @server]
                                            (stop-server)))]
  (facts "about /data/:dataset/slice/:slice"       
         (fact "it returns successfully when the dataset and slice exist"
               (prerequisite (#'cfpb.qu.data/get-metadata "good-dataset") => {:slices {:whoa {}}}
                             (#'cfpb.qu.query/execute "good-dataset" anything anything)
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
