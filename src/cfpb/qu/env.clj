(ns cfpb.qu.env
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [environ.core :as environ]))

(def default-env
  {:mongo-host "127.0.0.1"
   :mongo-port 27017
   :mongo-options {:connect-timeout 2000}
   :statsd-port 8125
   :http-ip "127.0.0.1"
   :http-port 3000
   :http-threads 4
   :http-queue-size 20480
   :log-file nil
   :log-level :info
   :dev false
   :integration false
   :api-name "Data API"})

(def ^{:doc "A map of environment variables."}
  env
  (let [env (merge default-env environ/env)
        config-file (:qu-config environ/env)]
    (if config-file
      (merge env
             (binding [*read-eval* false]
               (read-string (slurp config-file))))
      env)))
