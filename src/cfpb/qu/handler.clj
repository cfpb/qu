(ns cfpb.qu.handler
  (:require
   [clojure.string :as str]
   [ring.adapter.jetty :refer [run-jetty]]
   [ring.middleware
    [nested-params :refer [wrap-nested-params]]
    [params :refer [wrap-params]]
    [mime-extensions :refer [wrap-convert-extension-to-accept-header]]]
   [environ.core :refer [env]]
   [taoensso.timbre :as log]
   [clj-statsd :as sd]
   [cfpb.qu
    [data :as data]
    [routes :refer [app-routes]]
    [project :refer [project]]]
   [cfpb.qu.middleware
    [keyword-params :refer [wrap-keyword-params]]
    [logging :refer [wrap-with-logging]]]))

(defn init
  ([] (init (env :debug)))
  ([debug]
     (data/ensure-mongo-connection)
     (when debug
       (stencil.loader/set-cache (clojure.core.cache/ttl-cache-factory {} :ttl 0)))
     (log/set-config! [:prefix-fn]
                      (fn [{:keys [level timestamp hostname ns]}]
                        (str timestamp " " (-> level name str/upper-case)
                             " [" ns "]")))

     (when (:statsd-host project)
       (log/info (str "Configuring statsd: " (:statsd-host project) ":" (:statsd-port project)))
       (sd/setup (:statsd-host project) (:statsd-port project)))))

(defn destroy []
  (data/disconnect-mongo))

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
      wrap-convert-extension-to-accept-header))

(defn boot
  "Start our web API on the specified port."
  ([port] (run-jetty #'app {:port port}))
  ([port join?] (run-jetty #'app {:port port :join? join?})))
