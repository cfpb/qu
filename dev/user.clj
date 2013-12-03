(ns user
  (:require [alembic.still :refer [distill]]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [clojure.repl :refer :all]
            [clojure.tools.namespace.repl :refer [refresh refresh-all set-refresh-dirs]]
            [environ.core :refer [env]]
            [cfpb.qu.main :as main]
            [cfpb.qu.data :as data :refer [ensure-mongo-connection]]
            [cfpb.qu.loader :as loader :refer [load-dataset]]))

(set-refresh-dirs "src/" "dev/")

(def server (atom nil))

(defn stop-server
  []
  (if @server
    (@server)))

(defn start-server
  []
  (stop-server)
  (reset! server (main/-main)))
