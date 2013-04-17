(ns cfpb.qu.core
  (:require
   [cfpb.qu.handler :refer [boot]]
   [cfpb.qu.data :refer [ensure-mongo-connection]]
   [cfpb.qu.loader :refer [load-dataset]]))

(defn bootstrap
  "Load sample data for use in trying out Qu."
  []
  (ensure-mongo-connection)
  (load-dataset "county_taxes"))

(defn -main [& args]
  (boot 8080))

