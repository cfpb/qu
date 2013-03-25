(ns cfpb.qu.core
  (:require
   [cfpb.qu.handler :refer [boot]]))

(defn -main [& args]
  (boot 8080))

