(ns qu.logging
  (:require [clojure.string :as str]
            [qu.metrics :as metrics]
            [taoensso.timbre :as log :refer [error info]]))

(def ^:dynamic *log-id* "------")

(defn make-log-id
  []
  (subs (str (java.util.UUID/randomUUID)) 0 6))

(defn format-output-fn
  [{:keys [level throwable message timestamp]}
   ;; Any extra appender-specific opts:
   & [{:keys [nofonts?] :as appender-fmt-output-opts}]]
  (format "%s %s [%s] - %s%s"
          timestamp
          (-> level name str/upper-case)
          *log-id*
          (or message "")
          (or (log/stacktrace throwable "\n" (when nofonts? {})) "")))

(defn config
  [level file]
  (log/set-level! level)
  (log/set-config! [:timestamp-pattern] "yyyy-MM-dd'T'HH:mm:ssZZ")
  (log/set-config! [:fmt-output-fn] format-output-fn)  
  (when file
    (println "Sending log output to" file)
    (log/set-config! [:appenders :spit :enabled?] true)
    (log/set-config! [:shared-appender-config :spit-filename] file)
    (log/set-config! [:appenders :standard-out :enabled?] false)))

(defn- log-request-msg
  [verb {:keys [request-method uri remote-addr query-string params] :as req}]
  (let [uri (if query-string (str uri "?" query-string) uri)]
    (str/join " " [verb (str/upper-case (name request-method)) uri])))

(defn- log-request
  [{:keys [params] :as req}]
  (info (log-request-msg "Started" req)))

(defn- log-response
  [req {:keys [status] :as resp} total]
  (let [msg (log-request-msg "Finished" req)
        ms (str total "ms")]
    (if (and (number? status)
             (>= status 500))
      (error msg ms status)
      (info msg ms status))))

(defn- log-exception
  [req ex total]
  (metrics/increment "request.exception.count")
  (error (log-request-msg "Exception" req) (str total "ms"))
  (error ex)
  (error "--- END STACKTRACE ---"))

(defmacro log-with-time
  [level msg & body]
  `(let [start# (System/currentTimeMillis)
         result# (do ~@body)
         finish# (System/currentTimeMillis)
         ms# (- finish# start#)]
     (log/log ~level ~msg (str ms# "ms"))
     result#))





(defn wrap-with-logging
  [handler]
    (fn [request]
      (binding [*log-id* (make-log-id)]
            (let [start (System/currentTimeMillis)]
              (try
                (log-request request)
                (let [response (handler request)
                      finish (System/currentTimeMillis)
                      total  (- finish start)]
                  (log-response request response total)
                  (metrics/time-request request response total)
                  response)
                (catch Throwable ex
                  (let [finish (System/currentTimeMillis)
                        total (- finish start)]
                    (log-exception request ex total))
                  (throw ex)))))))
