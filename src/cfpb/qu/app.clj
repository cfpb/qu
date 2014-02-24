(ns cfpb.qu.app
  (:require
   [cfpb.qu.logging :as logging]
   [cfpb.qu.app.webserver :refer [new-webserver]]
   [cfpb.qu.app.mongo :refer [new-mongo]]
   [cfpb.qu.app.options :refer [inflate-options]]
   [cfpb.qu.cache :as qc]
   [taoensso.timbre :as log]
   [com.stuartsierra.component :as component]))

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

(defrecord QuSystem [options api db log cache-worker]
  component/Lifecycle

  (start [system]
    (component/start-system system [:log :db :api :cache-worker])
    (log/info "Started with settings" (str options))
    system)
  
  (stop [system]
    (component/stop-system system [:api :cache-worker :db :log])))

(defn new-qu-system [options]
  (let [{:keys [http dev log mongo] :as options} (inflate-options options)]
    (map->QuSystem {:options options
                    :db (new-mongo mongo)
                    :log (new-log log)
                    :api (new-webserver http dev)
                    :cache-worker (->CacheWorker)})))
