(ns cfpb.qu.main
  (:gen-class
   :main true)
  (:require
   [cfpb.qu
    [app :refer [new-qu-system]]
    [util :refer [->int]]
    [env :refer [env]]
    [logging :as logging]
    [project :refer [project]]]
   [cfpb.qu.metrics :as metrics]
   [clojure.string :as str]
   [taoensso.timbre :as log]
   [com.stuartsierra.component :as component])
  (:import [java.lang.management.ManagementFactory]))

(defn setup-statsd
  "Setup statsd to log metrics. Requires :statsd-host and :statsd-port
  to be in the project.clj file."
  []
  (log/info (str "Configuring metrics collection: " (env :statsd-host) ":" (env :statsd-port)))
  (metrics/setup (env :statsd-host) (env :statsd-port)))

(defn- print-live-threads
  []
  (let [mx-bean (java.lang.management.ManagementFactory/getThreadMXBean)
        stack (seq (.dumpAllThreads mx-bean true true))]
    (fn []
      (doseq [thread stack]
        (println thread)))))

(defn add-shutdown-hook
  "Add a shutdown hook that prints all live threads"
  []
  (.addShutdownHook (Runtime/getRuntime) (Thread. (print-live-threads))))

(defn default-log-options
  []
  (let [log-file (:log-file env)
        log-level (:log-level env)]
    {:file log-file
     :level log-level}))

(defn default-mongo-options
  []
  (let [uri (env :mongo-uri)
        hosts (env :mongo-hosts)
        host (env :mongo-host)
        port (->int (env :mongo-port))
        options (env :mongo-options {})
        auth (env :mongo-auth)]
    {:conn {:uri uri
            :hosts hosts
            :host host
            :port port}
     :options options
     :auth auth}))

(defn default-view-data
  []
  {:qu_version (:version @project)
   :build_number (:build-number @project)
   :build_url (:build-url @project)
   :base_url (:app-url @project)
   :api_name (:api-name env)
   :dev_mode (:dev env)})

(defn default-http-options
  []
  {:ip (:http-ip env)
   :port (->int (:http-port env))
   :thread (->int (:http-threads env))
   :queue-size (->int (:http-queue-size env))
   :view (default-view-data)})

(defn default-options
  []
  {:dev (:dev env)
   :view-data (default-view-data)
   :http (default-http-options)
   :log (default-log-options)
   :mongo (default-mongo-options)})

(defn -main
  [& args]
  (when (env :statsd-host)
    (setup-statsd))
  (when (:dev env)      
    (log/info "Dev mode enabled" (:dev env)))
  (add-shutdown-hook)
  (component/start (new-qu-system (default-options))))

