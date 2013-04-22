(ns cfpb.qu.resources
  "RESTful resources for datasets and slices. Uses
Liberator (http://clojure-liberator.github.com/) to handle exposing
the resources.

In Liberator, returning a map from a function adds that map to the
context passed to subsequent functions. We use that heavily in exists?
functions to return the resource that will be presented later."
  (:require
   [clojure.string :as str]
   [taoensso.timbre :as log]
   [liberator.core :refer [defresource request-method-in]]
   [monger.core :as mongo]
   [noir.response :refer [status]]
   [protoflex.parse :refer [parse]]
   [halresource.resource :as hal]
   [cfpb.qu.data :as data]
   [cfpb.qu.views :as views]
   [cfpb.qu.query :as query :refer [params->Query]]
   [cfpb.qu.query.parser :refer [where-expr]]))

(defn not-found [msg]
  (status
   404
   (views/layout-html
    (views/not-found-html msg))))

(defresource
  ^{:doc "Resource for the collection of datasets."}
  index
  :available-media-types ["text/html" "application/json" "application/xml"]
  :method-allowed? (request-method-in :get)
  :handle-ok (fn [{:keys [request representation]}]
               (let [datasets (data/get-datasets)
                     resource (hal/new-resource (:uri request))
                     embedded (map (fn [dataset]
                                     (hal/add-properties
                                      (hal/new-resource (str "/data/" (:name dataset)))
                                      (:info dataset))) datasets)
                     resource (reduce #(hal/add-resource %1 "dataset" %2) resource embedded)]
                 (views/index (:media-type representation) resource))))

(defresource
  ^{:doc "Resource for an individual dataset."}
  dataset
  :available-media-types ["text/html" "application/json" "application/xml"]  
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
  :handle-ok (fn [{:keys [request dataset metadata representation]}]
               (let [resource (-> (hal/new-resource (:uri request))
                                  (hal/add-link :rel "up" :href "/data")
                                  (hal/add-property :id dataset)
                                  (hal/add-properties (:info metadata))
                                  (hal/add-property :concepts (:concepts metadata)))
                     embedded (map (fn [[slice info]]
                                     (-> (hal/new-resource (str "/data/" dataset "/" (name slice)))
                                         (hal/add-property :id (name slice))
                                         (hal/add-properties info))) (:slices metadata))
                     resource (reduce #(hal/add-resource %1 "slice" %2) resource embedded)]
                 (views/dataset (:media-type representation) resource))))

(defresource
  ^{:doc "Resource for an individual slice."}
  slice
  :available-media-types ["text/html" "text/csv" "application/json" "application/xml"]
  :method-allowed? (request-method-in :get)
  :exists? (fn [{:keys [request]}]
             (let [dataset (get-in request [:params :dataset])
                   metadata (data/get-metadata dataset)
                   slice (get-in request [:params :slice])]
               (if-let [slicedef (get-in metadata [:slices (keyword slice)])]
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
               (let [headers (:headers request)
                     slicedef (get-in metadata [:slices slice])
                     query (params->Query (:params request) slicedef)
                     query (mongo/with-db (mongo/get-db dataset)
                             (query/execute (:table slicedef) query))
                     href (:uri request)
                     resource (-> (hal/new-resource href)
                                  (hal/add-link :rel "up" :href (str "/data/" dataset))
                                  (hal/add-link :rel "query"
                                                :href (str href "?$where={?where}&$orderBy={?orderBy}&$select={?select}&$offset={?offset}&$limit={?limit}")
                                                :templated true)
                                  (hal/add-properties {:dataset dataset :slice (name slice)})
                                  (hal/add-properties (-> query
                                                          (dissoc :slicedef :mongo :dimensions)
                                                          (assoc :results (:result query))
                                                          (dissoc :result))))
                     view-map {:dataset dataset
                               :metadata metadata
                               :slicedef slicedef
                               :headers headers
                               :dimensions (:dimensions query)}]
                 (views/slice (:media-type representation)
                              resource
                              view-map))))
