(ns cfpb.qu.routes
  (:require
   [compojure
    [core :refer [defroutes GET routes]]
    [route :as route]]
   [clojure.java.io :as io]
   [ring.util.response :refer [redirect]]
   [cfpb.qu.resources :as resources]
   [cfpb.qu.urls :refer :all]
   [cfpb.qu.swagger :as swagger]))

(defroutes app-routes
  "Create the app routes. Provides GET-only access to the list of
datasets, individual datasets, and slices. Static files are served
through Jetty, not through another web server."
  (GET "/" [] (redirect datasets-template))   
  (GET "/data.:extension" [] resources/index)
  (GET datasets-template [] resources/index)
  (GET "/data/:dataset.:extension" [dataset] resources/dataset)
  (GET dataset-template [dataset] resources/dataset)
  (GET "/data/:dataset/concept/:concept.:extension" [dataset concept] resources/concept)    
  (GET concept-template [dataset concept] resources/concept)
  (GET "/data/:dataset/slice/:slice/metadata.:extension" [dataset slice] resources/slice-metadata)
  (GET slice-metadata-template [dataset slice] resources/slice-metadata)  
  (GET "/data/:dataset/slice/:slice.:extension" [dataset slice] resources/slice-query)
  (GET slice-query-template [dataset slice] resources/slice-query)
  (GET swagger-resource-listing-template [:as req] (swagger/resource-listing-json req))
  (GET swagger-api-declaration-template [api :as req] (swagger/api-declaration-json api req))
  (route/resources "/static" {:root "static"})
  (route/not-found (resources/not-found)))
