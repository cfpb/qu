(ns cfpb.qu.env
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [environ.core :as environ]))

(def ^{:doc "A map of environment variables."}
  env
  (let [config-file (:qu-config environ/env)]
    (if config-file
      (binding [*read-eval* false]
        (read-string (slurp config-file)))
      environ/env)))
