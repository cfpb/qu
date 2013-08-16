(ns cfpb.qu.logging
  (:require
   [clojure.string :as str]
   [clj-statsd :as sd]
   [environ.core :refer [env]]   
   [taoensso.timbre :as log :refer [trace debug info warn error fatal spy]]))

(defn config
  []
  (let [log-file (:log-file env)
        log-level (:log-level env)]
    (log/set-level! log-level)
    (when log-file
      (log/set-config! [:appenders :spit :enabled?] true)
      (log/set-config! [:shared-appender-config :spit-filename] log-file)
      (log/set-config! [:appenders :standard-out :enabled?] false)))
  (log/set-config! [:prefix-fn]
                   (fn [{:keys [level timestamp hostname ns]}]
                     (str timestamp " " (-> level name str/upper-case)
                            " [" ns "]"))))

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
  (sd/increment "qu.request.exception")
  (error (str (log-request-msg "Exception on " req)
              " in " total " ms."))
  (error ex "--- END STACKTRACE ---"))

(defn wrap-with-logging
  [handler]
    (fn [request]
      (sd/with-timing "qu.request.time"
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
            (throw ex)))))))

