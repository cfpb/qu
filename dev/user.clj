(ns user
  (:require [alembic.still :refer [distill]]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [clojure.repl :refer :all]
            [clojure.tools.namespace.repl :refer [refresh refresh-all set-refresh-dirs]]
            [com.stuartsierra.component :as component]
            [cfpb.qu.main :as main]
            [cfpb.qu.app :refer [new-qu-system]]
            [cfpb.qu.app.options :refer [inflate-options]]
            [cfpb.qu.util :refer :all]
            [cfpb.qu.loader :as loader :refer [load-dataset]]))

(set-refresh-dirs "src/" "dev/")

(def system (atom nil))
(def options (atom {}))

(defn init
  ([] (init {}))
  ([options]
     (reset! system (new-qu-system (combine (main/default-options) options)))))

(defn start
  []
  (swap! system component/start))

(defn stop
  []
  (swap! system component/stop))

(defn go
  ([] (go {}))
  ([options]
     (init options)
     (start)))

(defn reset
  []
  (stop)
  (refresh :after 'user/go))
