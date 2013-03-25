(ns cfpb.qu.handler
  (:require
   [compojure
    [handler :as handler]]
   [ring.middleware
    [nested-params :refer [wrap-nested-params]]
    [params :refer [wrap-params]]
    [logger :as logger]]
   [cfpb.qu.middleware
    [keyword-params :refer [wrap-keyword-params]]]
   [ring.adapter.jetty :refer [run-jetty]]
   [noir.validation :as valid]
   [monger.core :as mongo]
   [com.ebaxt.enlive-partials :refer [handle-partials]]
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

(defn init []
  (data/ensure-mongo-connection))

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
      logger/wrap-with-logger
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
