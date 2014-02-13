(ns cfpb.qu.metrics
  "Captures application metrics. Currently uses clj-statsd to pipe metrics through StatsD"
  (:require
    [clj-statsd :as sd]))

(def prefix (delay (str "qu." (.getHostName (java.net.InetAddress/getLocalHost)) ".")))

(defn setup
  [host port]
  (sd/setup host port))

(defn prefix-metric
  "Ensure consistent prefix for all metrics"
  [key]
  (str @prefix key))

(defn increment
  [key & args]
  (apply sd/increment (prefix-metric key) args))

(defn decrement
  [key & args]
  (apply sd/decrement (prefix-metric key) args))

(defn gauge
  [key & args]
  (apply sd/gauge (prefix-metric key) args))

(defn unique
  [key & args]
  (apply sd/unique (prefix-metric key) args))

(defmacro with-sampled-timing
  [key rate & body]
  `(sd/with-sampled-timing (prefix-metric ~key) ~rate ~@body))

(defmacro with-timing
  [key & body]
  `(sd/with-timing (prefix-metric ~key) ~@body))