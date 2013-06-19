(ns user
  (:require [alembic.still :refer [distill]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [clojure.repl :refer :all]
            [clojure.tools.namespace.repl :refer [refresh refresh-all set-refresh-dirs]]
            [midje.repl :refer :all]
            [environ.core :refer [env]]            
            [taoensso.timbre :as log]
            [cfpb.qu.core :as core]
            [cfpb.qu.handler :as handler :refer [boot]]
            [cfpb.qu.data :as data :refer [ensure-mongo-connection]]
            [cfpb.qu.loader :as loader :refer [load-dataset]]))

(set-refresh-dirs "src/" "dev/")

(when-not (env :integration)
  (log/set-level! :info))

(def system nil)

(defn init
  "Constructs the dev system."
  []
  (alter-var-root #'system (constantly {})))

(defn start
  "Starts the dev system."
  []
  (handler/init true)
  (alter-var-root #'system                  
                  (fn [system]
                    (if-let [server (:server system)]
                      (do (doto server (.start))
                          system)
                      (assoc system :server (boot 8080 false))))))

(defn stop
  "Stops the dev system."
  []
  (handler/destroy)
  (alter-var-root #'system
                  (fn [system]
                    (when system
                      (if-let [server (:server system)]
                        (doto server (.stop))))
                    system)))

(defn go
  "Initializes the system and starts it running."
  []
  (init)
  (start))

(defn reset
  []
  (stop)
  (refresh :after 'user/go))
