(require '[taoensso.timbre :as log])
(require '[environ.core :refer [env]])
(require '[cfpb.qu.loader :as loader])
(require '[cfpb.qu.data :as data])

(log/set-level! :warn)

(when (env :integration)
  (try
    (data/ensure-mongo-connection)
    (loader/load-dataset "integration_test")
    (finally (data/disconnect-mongo))))

(when-not (env :integration)
  (change-defaults :fact-filter
                   (fn [metadata]
                     (let [file (:midje/file metadata)]
                       (not (re-find #"integration/" file))))))



