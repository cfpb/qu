(ns qu.app.webserver
  (:require [com.stuartsierra.component :as component]
            [liberator.dev :refer [wrap-trace]]
            [org.httpkit.server :refer [run-server]]
            [qu.etag :refer [wrap-etag]]
            [qu.logging :refer [wrap-with-logging]]
            [qu.middleware.keyword-params :refer [wrap-keyword-params]]
            [qu.middleware.stacktrace :as prod-stacktrace]
            [qu.middleware.uri-rewrite :refer [wrap-ignore-trailing-slash]]
            [qu.routes :refer [create-app-routes]]
            [ring.middleware.mime-extensions :refer [wrap-convert-extension-to-accept-header]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.reload :as reload]
            [ring.middleware.stacktrace :as dev-stacktrace]
            [ring.util.response :as response]
            [taoensso.timbre :as log]))

(defn- wrap-cors
  [handler]
  (fn [req]
    (let [resp (handler req)]
      (response/header resp "access-control-allow-origin" "*"))))

(defn get-handler
  "Create the entry point into the web API. We look for URI suffixes
  and strip them to set the Accept header before handing off the
  request to Compojure."
  [webserver]
  (let [handler (-> (create-app-routes webserver)
                    wrap-ignore-trailing-slash
                    wrap-keyword-params
                    wrap-nested-params
                    wrap-params
                    wrap-with-logging
                    wrap-etag
                    wrap-convert-extension-to-accept-header
                    wrap-cors)]
    (if (:dev webserver)
      (-> handler
          reload/wrap-reload
          (wrap-trace :header :ui)
          dev-stacktrace/wrap-stacktrace-web)
      (-> handler
          prod-stacktrace/wrap-stacktrace-web))))

(defrecord WebServer [ip port threads queue-size dev view]
  component/Lifecycle

  (start [component]
    (let [options {:ip ip
                   :port port
                   :thread threads
                   :queue-size queue-size}
          handler (get-handler component)]
      (log/info "Starting web server on" (str ip ":" port))
      (assoc component :server (run-server handler options))))

  (stop [component]
    (log/info "Stopping web server")
    (let [stop-server (:server component)]      
      (stop-server :timeout 100))
    (dissoc component :server)))

(defn new-webserver [options dev]
  (map->WebServer (merge {:dev dev} options)))
