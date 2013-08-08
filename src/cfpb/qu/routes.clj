(ns cfpb.qu.routes
  (:require
   [compojure
    [core :refer [defroutes GET]]
    [handler :as handler]
    [route :as route]]
   [clojure.java.io :as io]
   [noir.response :as response]
   [cfpb.qu.resources :as resources]))

(defroutes app-routes
  "Create the app routes. Provides GET-only access to the list of
datasets, individual datasets, and slices. Static files are served
through Jetty, not through another web server."
  (GET "/" [] (response/redirect "/data"))
  (GET "/data.:extension" [] resources/index)
  (GET "/data" [] resources/index)
  (GET "/data/:dataset.:extension" [dataset] resources/dataset)
  (GET "/data/:dataset" [dataset] resources/dataset)
  (GET "/data/:dataset/concept/:concept.:extension" [dataset concept] resources/concept)    
  (GET "/data/:dataset/concept/:concept" [dataset concept] resources/concept)
  (GET "/data/:dataset/slice/:slice/metadata.:extension" [dataset slice] resources/slice-metadata)
  (GET "/data/:dataset/slice/:slice/metadata" [dataset slice] resources/slice-metadata)  
  (GET "/data/:dataset/slice/:slice.:extension" [dataset slice] resources/slice-query)
  (GET "/data/:dataset/slice/:slice" [dataset slice] resources/slice-query)
  (route/resources "/static" {:root "static"})
  (route/not-found (resources/not-found "Route not found")))
