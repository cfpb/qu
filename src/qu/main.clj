(ns qu.main
  (:require [com.stuartsierra.component :as component]
            [qu.app :refer [new-qu-system]]
            [qu.env :refer [env]]
            [qu.project :refer [project]]
            [qu.util :refer [->bool ->int]]
            [taoensso.timbre :as log])
  (:gen-class :main true))

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
   :base_url (:app-url env)
   :api_name (:api-name env)
   :dev_mode (:dev env)})

(defn default-http-options
  []
  {:ip (:http-ip env)
   :port (->int (:http-port env))
   :threads (->int (:http-threads env))
   :queue-size (->int (:http-queue-size env))
   :view (default-view-data)})

(defn default-metrics-options
  []
  (if (:statsd-host env)
    {:provider :statsd
     :host (:statsd-host env)
     :port (->int (:statsd-port env))}
    {}))

(defn default-options
  []
  {:dev (->bool (:dev env))
   :http (default-http-options)
   :log (default-log-options)
   :mongo (default-mongo-options)
   :metrics (default-metrics-options)})

(defn load-config
  "Load configuration from an outside file."
  [config-file]
  (binding [*read-eval* false]
    (read-string (slurp config-file))))

(defn -main
  [& args]
  (let [config (if (= (count args) 1)
                 (load-config (first args))
                 (default-options))]
    (add-shutdown-hook)
    (component/start (new-qu-system config))
    (when (:dev env)      
      (log/info "Dev mode enabled"))))

