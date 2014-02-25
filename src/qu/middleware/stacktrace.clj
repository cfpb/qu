(ns qu.middleware.stacktrace
  "Catch exceptions and render web and log stacktraces for debugging."
  (:require [qu.views :as views]))

(defn- ex-response
  [req ex]
  (if-let [accept (get-in req [:headers "accept"])]
    (cond
     (re-find #"^json" accept) (views/json-error 500)
     :else (views/error-html))
    (views/error-html)))

(defn wrap-stacktrace-web
  "Wrap a handler such that exceptions are caught and no information
  is leaked to the user."
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception ex
        (ex-response request ex)))))
