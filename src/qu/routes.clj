(ns qu.routes
  (:require [compojure.core :refer [GET routes]]
            [compojure.route :as route]
            [qu.resources :as resources]
            [qu.swagger :as swagger]
            [qu.urls :refer :all]
            [ring.util.response :refer [redirect]]))

(defn create-app-routes
  "Create the app routes. Provides GET-only access to the list of
datasets, individual datasets, and slices. Static files are served
through Jetty, not through another web server."
  [webserver]
  (routes
   (GET "/" [] (redirect datasets-template))   
   (GET "/data.:extension" [] (resources/index webserver))
   (GET datasets-template [] (resources/index webserver))
   (GET "/data/:dataset.:extension" [dataset] (resources/dataset webserver))
   (GET dataset-template [dataset] (resources/dataset webserver))
   (GET "/data/:dataset/concept/:concept.:extension" [dataset concept] (resources/concept webserver))
   (GET concept-template [dataset concept] (resources/concept webserver))
   (GET "/data/:dataset/slice/:slice/metadata.:extension" [dataset slice] (resources/slice-metadata webserver))
   (GET slice-metadata-template [dataset slice] (resources/slice-metadata webserver))
   (GET "/data/:dataset/slice/:slice.:extension" [dataset slice] (resources/slice-query webserver))
   (GET slice-query-template [dataset slice] (resources/slice-query webserver))
   (GET swagger-resource-listing-template [:as req] (swagger/resource-listing-json req))
   (GET swagger-api-declaration-template [api :as req] (swagger/api-declaration-json api req))
   (route/resources "/static" {:root "static"})
   (route/not-found (resources/not-found))))


