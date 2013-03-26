(ns cfpb.qu.handler
  (:require
   [clojure.string :as str]
   [ring.adapter.jetty :refer [run-jetty]]   
   [ring.middleware
    [nested-params :refer [wrap-nested-params]]
    [params :refer [wrap-params]]
    [mime-extensions :refer [wrap-convert-extension-to-accept-header]]]
   [noir.validation :refer [wrap-noir-validation]]
   [com.ebaxt.enlive-partials :refer [handle-partials]]
   [taoensso.timbre :as log]
   [cfpb.qu
    [data :as data]
    [routes :refer [app-routes]]]
   [cfpb.qu.middleware
    [keyword-params :refer [wrap-keyword-params]]
    [logging :refer [wrap-with-logging]]]))

(defn init []
  (data/ensure-mongo-connection)
  (log/set-config! [:prefix-fn]
                   (fn [{:keys [level timestamp hostname ns]}]
                     (str timestamp " " (-> level name str/upper-case)
                          " [" ns "]"))))

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
      wrap-noir-validation
      wrap-convert-extension-to-accept-header
      (handle-partials "templates")))

(defn boot
  "Start our web API on the specified port."
  [port]
  (run-jetty #'app {:port port}))
