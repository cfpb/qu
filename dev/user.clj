(ns user
  (:require [alembic.still :refer [distill]]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [clojure.repl :refer :all]
            [clojure.tools.namespace.repl :refer [refresh refresh-all set-refresh-dirs]]
            [environ.core :refer [env]]
            [cfpb.qu.core :as core]
            [cfpb.qu.handler :as handler :refer [boot]]
            [cfpb.qu.data :as data :refer [ensure-mongo-connection]]
            [cfpb.qu.loader :as loader :refer [load-dataset]]
            stencil.loader))

(set-refresh-dirs "src/" "dev/")
(stencil.loader/set-cache (clojure.core.cache/ttl-cache-factory {} :ttl 0))
