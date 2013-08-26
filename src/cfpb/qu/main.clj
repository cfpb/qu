(ns cfpb.qu.main
  (:gen-class
   :main true)
  (:require
   [cfpb.qu
    [data :refer [ensure-mongo-connection]]
    [etag :refer [wrap-etag]]
    [project :refer [project]]    
    [routes :refer [app-routes]]
    [util :refer [->int]]
    [env :refer [env]]]
   [cfpb.qu.logging :as logging :refer [wrap-with-logging]]
   [cfpb.qu.middleware.keyword-params :refer [wrap-keyword-params]]
   [clj-statsd :as sd]
   [clojure.string :as str]
   [org.httpkit.server :refer [run-server]]   
   [ring.adapter.jetty :refer [run-jetty]]
   [ring.middleware
    [mime-extensions :refer [wrap-convert-extension-to-accept-header]]
    [nested-params :refer [wrap-nested-params]]
    [params :refer [wrap-params]]
    [reload :as reload]    
    [stacktrace :refer [wrap-stacktrace]]]
   [taoensso.timbre :as log]))

(def ^{:doc "The entry point into the web API. We look for URI
  suffixes and strip them to set the Accept header, then create a
  MongoDB connection for the request before handing off the request to
  Compojure."}
  app
  (-> app-routes
      wrap-keyword-params
      wrap-nested-params
      wrap-params
      wrap-with-logging
      wrap-etag
      wrap-convert-extension-to-accept-header))

(defn init
  []
  (let [dev (:dev env)]
    (when dev
      (stencil.loader/set-cache (clojure.core.cache/ttl-cache-factory {} :ttl 0)))
    (logging/config)))

(defn setup-statsd
  "Setup statsd to log metrics. Requires :statsd-host and :statsd-port
  to be in the project.clj file."
  []
  (log/info (str "Configuring statsd: " (env :statsd-host) ":" (env :statsd-port)))
  (sd/setup (env :statsd-host) (env :statsd-port)))

(defn -main
  [& args]
  (ensure-mongo-connection)  
  (init)
  (when (env :statsd-host)
    (setup-statsd))
  (let [handler (if (:dev env)
                  (-> app
                      reload/wrap-reload
                      wrap-stacktrace)
                  app)
        options {:ip (:http-ip env)
                 :port (->int (:http-port env))
                 :thread (->int (:http-threads env))
                 :queue-size (->int (:http-queue-size env))}]
    (log/info "Starting server on" (str (:ip options) ":" (:port options)))
    (when (:dev env)
      (log/info "Dev mode enabled"))
    (run-server handler options)))

