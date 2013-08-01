(ns cfpb.qu.core
  (:require
   [cfpb.qu.handler :refer [app init]]
   [cfpb.qu.data :refer [ensure-mongo-connection]]
   [cfpb.qu.loader :refer [load-dataset]]
   [ring.middleware.reload :as reload]
   [ring.middleware.stacktrace :refer [wrap-stacktrace]]
   [org.httpkit.server :refer [run-server]]
   [environ.core :refer [env]]))

(defn bootstrap
  "Load sample data for use in trying out Qu."
  []
  (ensure-mongo-connection)
  (load-dataset "county_taxes"))

(defn -main [& args]
  (init)
  (let [handler (if (env :dev)
                  (do
                    (println "Starting server on port 8080 in dev mode")
                    (-> app
                        reload/wrap-reload
                        wrap-stacktrace))
                  app)]
    (run-server handler {:port 8080})))

