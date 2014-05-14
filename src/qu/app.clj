(ns qu.app
  (:require [com.stuartsierra.component :as component]
            [qu.app.mongo :refer [new-mongo]]
            [qu.app.options :refer [inflate-options]]
            [qu.app.webserver :refer [new-webserver]]
            [qu.cache :as qc]
            [qu.logging :as logging]
            [qu.metrics :as metrics]
            [taoensso.timbre :as log]))

(defrecord Log [level file]
  component/Lifecycle

  (start [component]
    (logging/config level file)
    component)

  (stop [component]
    component))

(defn new-log [options]
  (map->Log options))

(defrecord CacheWorker []
  component/Lifecycle

  (start [component]
    (let [cache (qc/create-query-cache)
          worker (qc/create-worker cache)]
      (assoc component
        :worker worker
        :worker-agent (qc/start-worker worker))))

  (stop [component]
    (let [worker-agent (:worker-agent component)]
      (qc/stop-worker worker-agent)
      component)))

(defrecord Metrics [provider host port]
  component/Lifecycle

  (start [component]
    (when provider
      (metrics/setup host port))
    component)

  (stop [component]
    component))

(defn new-metrics [options]
  (map->Metrics options))

(def components [:log :db :api :cache-worker :metrics])

(defrecord QuSystem [options api db log cache-worker]
  component/Lifecycle

  (start [system]
    (let [system (component/start-system system components)]
      (log/info "Started with settings" (str (update-in options [:mongo :auth] (fn [_] str "*****"))))
      system))
  
  (stop [system]
    (component/stop-system system components)))

(defn new-qu-system [options]
  (let [{:keys [http dev log mongo metrics] :as options} (inflate-options options)]
    (map->QuSystem {:options options
                    :db (new-mongo mongo)
                    :log (new-log log)
                    :api (new-webserver http dev)
                    :cache-worker (->CacheWorker)
                    :metrics (new-metrics metrics)
                    })))
