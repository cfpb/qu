(require '[taoensso.timbre :as log])
(require '[environ.core :refer [env]])
(require '[cfpb.qu.loader :as loader])
(require '[cfpb.qu.data :as data])

(log/set-level! :warn)

(when (env :integration)
  (try
    (data/ensure-mongo-connection)
    (loader/load-dataset "county_taxes")
    (finally (data/disconnect-mongo))))

(change-defaults :fact-filter
                 (fn [metadata]
                   (let [file (:midje/file metadata)]
                     (if (env :integration)
                       (re-find #"integration/" file)
                       (not (re-find #"integration/" file))))))



