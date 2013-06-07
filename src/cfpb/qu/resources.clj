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
   [liberator.representation :refer [ring-response]]
   [noir.response :refer [status]]
   [protoflex.parse :refer [parse]]
   [halresource.resource :as hal]
   [clojurewerkz.urly.core :as url :refer [url-like]]
   [cfpb.qu.urls :as urls]
   [cfpb.qu.data :as data]
   [cfpb.qu.views :as views]
   [cfpb.qu.query :as query :refer [params->Query]]))

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
                                      (hal/new-resource (urls/dataset-path (:name dataset)))
                                      (:info dataset))) datasets)
                     resource (reduce #(hal/add-resource %1 "dataset" %2) resource embedded)]
                 (views/index request (:media-type representation) resource))))

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
                                  (hal/add-link :rel "up" :href (urls/index-path))
                                  (hal/add-property :id dataset)
                                  (hal/add-properties (:info metadata))
                                  (hal/add-property :concepts (:concepts metadata)))
                     embedded (map (fn [[slice info]]
                                     (-> (hal/new-resource (urls/slice-path dataset (name slice)))
                                         (hal/add-property :id (name slice))
                                         (hal/add-property
                                          :name
                                          (get-in info [:info :name] (name slice)))
                                         (hal/add-properties info))) (:slices metadata))
                     resource (reduce #(hal/add-resource %1 "slice" %2) resource embedded)]
                 (views/dataset request (:media-type representation) resource))))

(defn- templated-url
  "Build the templated URL for slice queries."
  [base-href clauses]
  (str base-href "?"
       (str/join "&"
                 (map
                  (comp #(str "$" % "={?" % "}") name)
                  clauses))))

(defn- slice-resource
  "Build a HAL resource for a slice."
  [dataset slice request query]
  (let [base-href (:uri request)
        href (url-like (if-let [query-string (:query-string request)]
                         (str base-href "?" query-string)
                         base-href))
        result (:result query)
        clauses (map (comp keyword :key) views/clauses)
        page (:page query)]
    (-> (hal/new-resource href)
        (hal/add-link :rel "up" :href (urls/dataset-path dataset))
        (hal/add-link :rel "query"
                      :href (templated-url base-href clauses)
                      :templated true)
        (hal/add-properties {:dataset dataset
                             :slice (name slice)
                             :size (:size result)
                             :total (:total result)
                             :page page
                             :query (select-keys query clauses)
                             :errors (:errors query)
                             :dimensions (:dimensions query)
                             :results (:data result)}))))

(defresource
  ^{:doc "Resource for an individual slice."}
  slice
  :available-media-types ["text/html" "text/csv" "application/json" "application/xml" "text/javascript"]
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
                     query (params->Query (:params request) metadata slice)
                     query (query/execute dataset slice query)
                     resource (slice-resource dataset slice request query)
                     view-map {:base-href (:uri request)
                               :query query
                               :metadata metadata
                               :slicedef slicedef
                               :headers headers
                               :dimensions (:dimensions query)
                               :callback (:callback query)}
                     response-body (views/slice request
                                                (:media-type representation)
                                                resource
                                                view-map)]
                 (if (query/valid? query)
                   response-body
                   (ring-response {:status 400 :body response-body})))))
