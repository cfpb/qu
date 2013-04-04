(ns cfpb.qu.resources
  "RESTful resources for datasets and slices. Uses
Liberator (http://clojure-liberator.github.com/) to handle exposing
the resources.

In Liberator, returning a map from a function adds that map to the
context passed to subsequent functions. We use that heavily in exists?
functions to return the resource that will be presented later."
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :refer [info error]]
   [liberator.core :refer [defresource request-method-in]]
   [monger.core :as mongo]
   [noir.validation :as valid]
   [noir.response :refer [status]]
   [protoflex.parse :refer [parse]]
   [cfpb.qu.data :as data]
   [cfpb.qu.views :as views]
   [cfpb.qu.query.parser :refer [where-expr]]))

(defn index [_]
  (views/layout-html
   (views/index-html
    (data/get-datasets))))

(defn not-found [msg]
  (status
   404
   (views/layout-html
    (views/not-found-html msg))))

(defresource
  ^{:doc "Resource for an individual dataset."}
  dataset
  :method-allowed? (request-method-in :get)
  :exists? (fn [{:keys [request]}]
             (let [dataset (get-in request [:params :dataset])
                   metadata (data/get-metadata dataset)]
               (if metadata
                 {:dataset dataset
                  :metadata metadata})))
  :handle-not-found (fn [{:keys [request representation]}]                      
                      (let [dataset (get-in request [:params :dataset])
                            message (str "No such dataset: " dataset)]
                        (case (:media-type representation)
                          "text/html" (not-found message)
                          message)))
  :handle-ok (fn [{:keys [dataset metadata]}]
               (apply str
                      (views/layout-html
                       (views/dataset-html dataset metadata))))
  :available-media-types ["text/html" "text/plain;q=0.8"])


(defn where-parses? [where]
  (try
    (or (str/blank? where)
        (parse where-expr where))
    (catch Exception e
      false)))

(defn valid-params? [{where :$where}]
  (valid/rule (where-parses? where)
               [:$where "The WHERE clause does not parse correctly."])
  (not (valid/errors? :$where)))

(defresource
  ^{:doc "Resource for an individual slice."}
  slice
  :available-media-types ["text/html" "text/csv" "application/json"]
  :method-allowed? (request-method-in :get)
  :malformed? (fn [{:keys [request]}]
                (let [media-type (get-in request [:headers "accept"])]
                  (not (or (valid-params? (:params request))
                           (= media-type "text/html")))))
  :handle-malformed (fn [{:keys [request representation]}]
                      (let [media-type (get-in request [:headers "accept"])]
                        (case media-type
                          "application/json" (views/json-error 400 {:errors @valid/*errors*})
                          {:status 400 :body (valid/get-errors)})))
  :exists? (fn [{:keys [request]}]
             (let [dataset (get-in request [:params :dataset])
                   metadata (data/get-metadata dataset)
                   slice (get-in request [:params :slice])]
               (if-let [slice-def (get-in metadata [:slices (keyword slice)])]
                 {:dataset dataset
                  :metadata metadata
                  :slice (keyword slice)})))
  :handle-not-found (fn [{:keys [request representation]}]
                      (let [dataset (get-in request [:params :dataset])
                            slice (get-in request [:params :slice])
                            message (str "No such slice: " dataset "/" slice)]
                        (case (:media-type representation)
                          "text/html" (not-found message)
                          message)))
  :handle-ok (fn [{:keys [dataset metadata slice request representation]}]
               (let [params (:params request)
                     headers (:headers request)
                     slice-def (get-in metadata [:slices slice])
                     view-map {:dataset dataset
                               :slice-def slice-def
                               :params params
                               :headers headers}]
                 (mongo/with-db (mongo/get-db dataset)
                   (let [data (data/get-data slice-def params)]
                     (views/slice (:media-type representation)
                                  data
                                  view-map))))))
