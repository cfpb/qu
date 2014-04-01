(ns user
  (:require [alembic.still :refer [distill]]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [clojure.repl :refer :all]
            [clojure.test :as test]
            [clojure.tools.namespace.find :refer [find-namespaces-in-dir]]
            [clojure.java.io :refer [as-file]]
            [clojure.tools.namespace.repl :refer [refresh refresh-all set-refresh-dirs]]
            [environ.core :refer [env]]
            [cfpb.qu.main :as main]
            [cfpb.qu.data :as data :refer [ensure-mongo-connection]]
            [cfpb.qu.loader :as loader :refer [load-dataset]]))

(set-refresh-dirs "src/" "dev/" "test/")

(defn load-tests
  []
  (doseq [namespace (find-namespaces-in-dir (as-file "test"))]
    (require namespace)))

(defn run-unit-tests
  []
  (refresh)
  (apply test/run-tests (remove (fn [namespace] (:integration (meta namespace))) (all-ns))))

(defn run-all-tests
  []
  (refresh)
  (test/run-all-tests))

(def server (atom nil))

(defn stop-server
  []
  (if @server
    (@server)))

(defn start-server
  []
  (stop-server)
  (reset! server (main/-main)))
