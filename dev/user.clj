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

(defn deep-merge
  [map1 map2]
  (if (not (and (map? map1) (map? map2)))
    (or map2 map1)
    (merge-with deep-merge map1 map2)))

(def system (atom nil))
(def options (atom {}))

(defn init
  []
  (reset! system (new-qu-system (deep-merge (main/default-options) @options))))

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
