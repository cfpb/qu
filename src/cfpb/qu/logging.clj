(ns cfpb.qu.logging
  (:require
   [clojure.string :as str]
   [cfpb.qu
    [metrics :as metrics]
    [env :refer [env]]]
   [clj-statsd :as sd]
   [taoensso.timbre :as log :refer [trace debug info warn error fatal spy]]))

(def ^:dynamic *log-id* "------")

(defn make-log-id
  []
  (subs (str (java.util.UUID/randomUUID)) 0 6))

(defn prefix-fn
  [{:keys [level timestamp hostname ns]}]
  (str/join " " [timestamp (-> level name str/upper-case) (str "[" *log-id* "]")]))

(defn config
  []
  (let [log-file (:log-file env)
        log-level (:log-level env)]
    (log/set-level! log-level)
    (when log-file
      (println "Sending log output to" (:log-file env) )
      (log/set-config! [:appenders :spit :enabled?] true)
      (log/set-config! [:shared-appender-config :spit-filename] log-file)
      (log/set-config! [:appenders :standard-out :enabled?] false)))
  (log/set-config! [:timestamp-pattern] "yyyy-MM-dd'T'HH:mm:ssZZ")
  (log/set-config! [:prefix-fn] prefix-fn))

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
  (metrics/increment "request.exception")
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

(defn metrics-path
  [uri]
  (let [parts (str/split uri #"\.")]
    (cond
      (str/blank? (first parts)) "/data.html"
      (= 1 (count parts)) (str uri ".html")
      :else uri)))

(defn wrap-with-logging
  [handler]
    (fn [request]
      (binding [*log-id* (make-log-id)]
          (metrics/with-timing (str "request.url." (metrics-path (:uri request)) ".time")
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
                  (throw ex))))))))

