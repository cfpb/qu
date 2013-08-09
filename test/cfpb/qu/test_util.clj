(ns cfpb.qu.test-util
  (:require [midje.sweet :refer :all]
            [org.httpkit.server :refer [run-server]]
            [org.httpkit.client :as client]
            [cfpb.qu.main :refer [app]]))

(def port 4545)
(def server (atom nil))

(defn GET
  [path]
  @(client/get (str "http://localhost:" port path)))

(defmacro with-server
  [& body]
  `(with-state-changes [(before :facts (do (when @server (@server))
                                           (reset! server (run-server app {:port ~port}))))
                        (after :facts (let [stop-server# @server]
                                        (stop-server#)))]
    ~@body))
