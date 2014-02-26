(ns qu.urls
  (:require [clojurewerkz.route-one.core :refer [defroute]]))

(defroute datasets       "/data")
(defroute dataset        "/data/:dataset")
(defroute concept        "/data/:dataset/concept/:concept")
(defroute slice-query    "/data/:dataset/slice/:slice")
(defroute slice-metadata "/data/:dataset/slice/:slice/metadata")

(defroute swagger-resource-listing "/api-docs")
(defroute swagger-api-declaration  "/api-docs/:api")
