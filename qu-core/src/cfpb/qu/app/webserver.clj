(ns cfpb.qu.app.webserver
  (:gen-class
   :main true)
  (:require
   [cfpb.qu
    [etag :refer [wrap-etag]]
    [routes :refer [app-routes]]
    [logging :as logging :refer [wrap-with-logging]]]
   [cfpb.qu.middleware.keyword-params :refer [wrap-keyword-params]]
   [cfpb.qu.middleware.stacktrace :as prod-stacktrace]
   [cfpb.qu.middleware.uri-rewrite :refer [wrap-ignore-trailing-slash]]
   [ring.middleware
    [mime-extensions :refer [wrap-convert-extension-to-accept-header]]
    [nested-params :refer [wrap-nested-params]]
    [params :refer [wrap-params]]
    [reload :as reload]    
    [stacktrace :as dev-stacktrace]]
   [ring.util.response :as response]
   [taoensso.timbre :as log]
   [org.httpkit.server :refer [run-server]]   
   [com.stuartsierra.component :as component]))

(defn- wrap-cors
  [handler]
  (fn [req]
    (let [resp (handler req)]
      (response/header resp "access-control-allow-origin" "*"))))

(defn get-handler
  "Create the entry point into the web API. We look for URI suffixes
  and strip them to set the Accept header before handing off the
  request to Compojure."
  [dev]
  (let [handler (-> app-routes
                    wrap-ignore-trailing-slash
                    wrap-keyword-params
                    wrap-nested-params
                    wrap-params
                    wrap-with-logging
                    wrap-etag
                    wrap-convert-extension-to-accept-header
                    wrap-cors)]
    (if dev
      (-> handler
          reload/wrap-reload
          dev-stacktrace/wrap-stacktrace-web)
      (-> handler
          prod-stacktrace/wrap-stacktrace-web))))

(defrecord WebServer [ip port thread queue-size dev]
  component/Lifecycle

  (start [component]
    (let [options {:ip ip
                   :port port
                   :thread thread
                   :queue-size queue-size}
          handler (get-handler dev)]
      (log/info "Starting web server on" (str ip ":" port))
      (assoc component :server (run-server handler options))))

  (stop [component]
    (log/info "Stopping web server")
    (let [stop-server (:server component)]      
      (stop-server :timeout 100))
    (dissoc component :server)))

(defn new-webserver [options dev]
  (map->WebServer (merge {:dev dev} options)))
