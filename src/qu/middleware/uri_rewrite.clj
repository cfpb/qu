(ns qu.middleware.uri-rewrite
  (:require [clojure.string :as str]))

(defn with-uri-rewrite
  "Rewrites a request uri with the result of calling f with the
   request's original uri.  If f returns nil the handler is not called."
  [handler f]
  (fn [request]
    (let [uri (:uri request)
          rewrite (f uri)]
      (if rewrite
        (handler (assoc request :uri rewrite))
        nil))))

(defn- uri-snip-slash
  "Removes a trailing slash from all uris except \"/\"."
  [uri]
  (if (= "/" uri)
    uri
    (str/replace uri #"/$" "")))

(defn wrap-ignore-trailing-slash
  "Makes routes match regardless of whether or not a uri ends in a slash."
  [handler]
  (with-uri-rewrite handler uri-snip-slash))
