(require '[taoensso.timbre :as log])
(log/set-level! :warn)

(change-defaults :fact-filter
                 (fn [metadata]
                   (let [file (:midje/file metadata)]
                     (not (re-find #"integration/" file)))))



