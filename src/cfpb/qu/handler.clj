(ns cfpb.qu.handler
  (:require
   [clojure.string :as str]
   [compojure
    [handler :as handler]]
   [ring.middleware
    [nested-params :refer [wrap-nested-params]]
    [params :refer [wrap-params]]]
   [cfpb.qu.middleware
    [keyword-params :refer [wrap-keyword-params]]]
   [ring.adapter.jetty :refer [run-jetty]]
   [noir.validation :as valid]
   [monger.core :as mongo]
   [com.ebaxt.enlive-partials :refer [handle-partials]]
   [taoensso.timbre :as log :refer [trace debug info warn error fatal spy]]
   [cfpb.qu
    [data :as data]
    [routes :refer [app-routes]]
    [resources :as resources]]))

(defn- wrap-convert-suffix-to-accept-header
  "A URI identifies a resource, not a representation. But conventional
practice often uses the suffix of a URI to indicate the media-type of
the resource. This is understandable given that browsers don't allow
uses control over the Accept header. However, if we drop the suffix
from the URI prior to processing it we can support a rich variety of
representations as well as allowing the user a degree of control by
via the URL. This function matches the suffix of a URI to a mapping
between suffixes and media-types. If a match is found, the suffix is
dropped from the URI and an Accept header is added to indicate the
media-type preference."
  [handler media-type-map]
  (fn [request]
    (let [uri (:uri request)]
      (if-let [[suffix media-type] (some (fn [[k v]] (when (.endsWith uri k) [k v])) media-type-map)]
        (-> request
            (assoc-in [:headers "accept"] media-type)
            (assoc :uri (.substring uri 0 (- (count uri) (count suffix))))
            handler)
        (handler request)))))

(defn- log-request-msg
  [verb {:keys [request-method uri remote-addr query-string params] :as req}]
  (str verb
       " "
       (str/upper-case (name request-method))
       " "
       uri
       (if query-string (str "?" query-string))
       " for " remote-addr))

(defn- log-request
  [{:keys [params] :as req}]
  (info (log-request-msg "Started" req))
  (if params
    (info (str "Params:" params))))

(defn- log-response
  [req {:keys [status] :as resp} total]
  (let [msg (str (log-request-msg "Finished" req)
                 " in " total " ms.  Status: "
                 status)]
    (if (and (number? status)
             (>= status 500))
      (error msg)
      (info msg))))

(defn- log-exception
  [req ex total]
  (error (str (log-request-msg "Exception on " req)
              " in " total " ms."))
  (error ex "--- END STACKTRACE ---"))

(defn- wrap-logging
  [handler]
  (fn [request]
    (let [start (System/currentTimeMillis)]
      (try
        (log-request request)
        (let [response (handler request)
              finish (System/currentTimeMillis)
              total  (- finish start)]
          (log-response request response total)
          response)
        (catch Throwable ex
          (let [finish (System/currentTimeMillis)
                total (- finish start)]
            (log-exception request ex total))
          (throw ex))))))

(defn init []
  (data/ensure-mongo-connection)
  (log/set-config! [:prefix-fn]
                      (fn [{:keys [level timestamp hostname ns]}]
                        (str timestamp " " (-> level name str/upper-case)
                             " [" ns "]"))))

(defn destroy []
  (mongo/disconnect!))

(def ^{:doc "The entry point into the web API. We look for URI
  suffixes and strip them to set the Accept header, then create a
  MongoDB connection for the request before handing off the request to
  Compojure."}
  app
  (-> app-routes
      wrap-keyword-params
      wrap-nested-params
      wrap-params
      wrap-logging
      valid/wrap-noir-validation
      (wrap-convert-suffix-to-accept-header
       {".html" "text/html"
        ".json" "application/json"
        ".csv" "text/csv"})
      (handle-partials "templates")))

(defn boot
  "Start our web API on the specified port."
  [port]
  (run-jetty #'app {:port port}))
