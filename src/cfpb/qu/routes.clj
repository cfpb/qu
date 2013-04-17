(ns cfpb.qu.routes
  (:require
   [compojure
    [core :refer [defroutes GET]]
    [handler :as handler]
    [route :as route]]
   [clojure.java.io :as io]
   [cfpb.qu.resources :as resources]))

(defroutes app-routes
  "Create the app routes. Provides GET-only access to the list of
datasets, individual datasets, and slices. Static files are served
through Jetty, not through another web server."
  (GET "/" [] resources/index)
  (GET "/data.:extension" [] resources/index)  
  (GET "/data" [] resources/index)
  (GET "/data/:dataset" [dataset] resources/dataset)
  (GET "/data/:dataset/:slice.:extension" [dataset slice] resources/slice)
  (GET "/data/:dataset/:slice" [dataset slice] resources/slice)
  (route/files "/static" {:root (.getPath (io/resource "static/"))})
  (route/not-found (resources/not-found "Route not found")))
