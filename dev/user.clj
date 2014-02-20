(ns user
  (:require [alembic.still :refer [distill]]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [clojure.repl :refer :all]
            [clojure.tools.namespace.repl :refer [refresh refresh-all set-refresh-dirs]]
            [com.stuartsierra.component :as component]
            [cfpb.qu.main :as main]
            [cfpb.qu.app :refer [new-qu-system]]
            [cfpb.qu.loader :as loader :refer [load-dataset]]))

(set-refresh-dirs "src/" "dev/")

(def system (atom nil))

(defn init
  []
  (reset! system (new-qu-system (main/default-options))))

(defn start
  []
  (swap! system component/start))

(defn stop
  []
  (swap! system component/stop))

(defn go
  []
  (init)
  (start))

(defn reset
  []
  (stop)
  (refresh :after 'user/go))
