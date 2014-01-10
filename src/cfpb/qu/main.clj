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
    [env :refer [env]]
    [logging :as logging :refer [wrap-with-logging]]]
   [cfpb.qu.cache :as qc]
   [cfpb.qu.middleware.keyword-params :refer [wrap-keyword-params]]
   [cfpb.qu.middleware.stacktrace :as prod-stacktrace]
   [cfpb.qu.middleware.uri-rewrite :refer [wrap-ignore-trailing-slash]]
   [cfpb.qu.metrics :as metrics]
   [clojure.string :as str]
   [org.httpkit.server :refer [run-server]]   
   [ring.middleware
    [mime-extensions :refer [wrap-convert-extension-to-accept-header]]
    [nested-params :refer [wrap-nested-params]]
    [params :refer [wrap-params]]
    [reload :as reload]    
    [stacktrace :as dev-stacktrace]]
   [ring.util.response :as response]
   [taoensso.timbre :as log])
  (:import [java.lang.management.ManagementFactory]))

(defn- wrap-cors
  [handler]
  (fn [req]
    (let [resp (handler req)]
      (response/header resp "access-control-allow-origin" "*"))))

(def ^{:doc "The entry point into the web API. We look for URI
  suffixes and strip them to set the Accept header, then create a
  MongoDB connection for the request before handing off the request to
  Compojure."}
  app
  (-> app-routes
      wrap-ignore-trailing-slash
      wrap-keyword-params
      wrap-nested-params
      wrap-params
      wrap-with-logging
      wrap-etag
      wrap-convert-extension-to-accept-header
      wrap-cors))

(defn init
  []
  (let [dev (:dev env)]
    (logging/config)))

(defn setup-statsd
  "Setup statsd to log metrics. Requires :statsd-host and :statsd-port
  to be in the project.clj file."
  []
  (log/info (str "Configuring metrics collection: " (env :statsd-host) ":" (env :statsd-port)))
  (metrics/setup (env :statsd-host) (env :statsd-port)))

(defn start-cache-worker
  "Startup a query cache worker."
  []
  (let [cache (qc/create-query-cache)
        worker (qc/create-worker cache)]
    (qc/start-worker worker)))

(defn- print-live-threads
  []
  (let [mx-bean (java.lang.management.ManagementFactory/getThreadMXBean)
        stack (seq (.dumpAllThreads mx-bean true true))]
    (fn []
      (doseq [thread stack]
        (println thread)))))

(defn add-shutdown-hook
  "Add a shutdown hook that prints all live threads"
  []
  (.addShutdownHook (Runtime/getRuntime) (Thread. (print-live-threads))))

(defn -main
  [& args]
  (ensure-mongo-connection)  
  (init)
  (when (env :statsd-host)
    (setup-statsd))
  (let [handler (if (:dev env)
                  (-> app
                      reload/wrap-reload
                      dev-stacktrace/wrap-stacktrace-web)
                  (-> app
                      prod-stacktrace/wrap-stacktrace-web))
        options {:ip (:http-ip env)
                 :port (->int (:http-port env))
                 :thread (->int (:http-threads env))
                 :queue-size (->int (:http-queue-size env))}]
    (log/info "Starting server on" (str (:ip options) ":" (:port options)))
    (when (:dev env)      
      (log/info "Dev mode enabled" (:dev env)))
    (add-shutdown-hook)
    (start-cache-worker)
    (run-server handler options)))

