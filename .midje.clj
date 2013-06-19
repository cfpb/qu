(require '[taoensso.timbre :as log])
(require '[environ.core :refer [env]])

(log/set-level! :error)

(when-not (env :integration)
  (change-defaults :fact-filter
                   (fn [metadata]
                     (let [file (:midje/file metadata)]
                       (not (re-find #"integration/" file))))))



