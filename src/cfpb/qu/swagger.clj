(ns cfpb.qu.swagger
  (:require
   [clojure.string :as str :refer [capitalize]]
   [cfpb.qu.util :refer :all]
   [cfpb.qu.data :as data]
   [cfpb.qu.urls :as urls]
   [noir.response :as response]
   [lonocloud.synthread :as ->]
   [clojurewerkz.route-one.core :as route]
   [clojurewerkz.urly.core :as urly :refer [url-like]]))

(defn- versions
  [m]
  (merge m {:apiVersion "1.0"
            :swaggerVersion "1.2"}))

(defn- base-path
  [m req]
  (merge m {:basePath (base-url req)}))

(defn resource-listing
  [req]
  (let [base-url (base-url req)]
    (route/with-base-url base-url
      (let [datasets (data/get-dataset-names)
            dataset-apis (map #(into {} {:path (route/with-base-url base-url
                                                 (urls/swagger-api-declaration-url :api %))
                                         :summary (str "Operations about " %)}) datasets)]
        (versions
         {:apis (concat [{:path (urls/swagger-api-declaration-url :api "data")
                          :description "Operations about datasets."}]
                        dataset-apis
                        []
                        )})))))

(defn- get-api
  [& {:keys [path] :as m}]
  {:path path
   :operations [(-> m
                    (assoc :method "GET")
                    (dissoc :path))]})

(defn- api-param
  [data-type param-name & {:as m}]
  (let [enum (:enum m)
        m (if enum
            (assoc m :allowableValues {:valueType "LIST"
                                       :values enum})
            m)
        m (dissoc m :enum)]
    (-> {:dataType (name data-type)
         :name (name param-name)}
        (merge m))))

(defn data-declaration
  [req]
  (-> {:resourcePath (urls/datasets-path)
       :produces ["application/json" "application/xml"]
       :models {}
       :apis [(get-api :path (urls/datasets-path)
                       :nickname "getDatasets"
                       :summary "Get a list of all datasets."
                       :produces ["application/json" "application/xml"]
                       :parameters [])
              (get-api :path (urls/dataset-path :dataset "{dataset}")
                       :nickname "getDataset"
                       :summary "Get metadata about a dataset."
                       :produces ["application/json" "application/xml"]
                       :parameters [(api-param :string "dataset"
                                               :paramType "path"
                                               :description "Name of dataset"
                                               :required true
                                               :enum (data/get-dataset-names))])]}
      (versions)
      (base-path req)))

(defn dataset-declaration
  [dataset req]
  (let [metadata (data/get-metadata dataset)
        slices (sort (map name (keys (:slices metadata))))
        concepts (sort (map name (keys (:concepts metadata))))]
    (-> {:resourcePath (urls/dataset-path :dataset dataset)
         :produces ["application/json" "application/xml"]
         :models []
         :apis [(get-api :path (urls/dataset-path :dataset dataset)
                         :nickname (str "getDataset" (capitalize dataset))
                         :summary "Get metadata for this dataset."  
                         :parameters [])
                (get-api :path (urls/concept-path :dataset dataset :concept "{concept}")
                         :nickname (str "getConcept" (capitalize dataset))
                         :summary "Get information about a particular concept in this dataset."
                         :parameters [(api-param :string "concept"
                                                 :paramType "path"
                                                 :description "Name of concept"
                                                 :required true
                                                 :enum concepts)])
                (get-api :path (urls/slice-query-path :dataset dataset :slice "{slice}")
                         :nickname (str "querySlice" (capitalize dataset))
                         :summary "Query a slice in this dataset."
                         :parameters [(api-param :string "slice"
                                                 :paramType "path"
                                                 :description "Name of slice"
                                                 :required true
                                                 :enum slices)])
                (get-api :path (urls/slice-metadata-path :dataset dataset :slice "{slice}")
                         :nickname (str "getSliceMetadata" (capitalize dataset))
                         :summary "Get the metadata for a slice in this dataset."
                         :parameters [(api-param :string "slice"
                                                 :paramType "path"
                                                 :description "Name of slice"
                                                 :required true
                                                 :enum slices)])]}
        (versions)
        (base-path req))))

(defn resource-listing-json
  [req]
  (response/json (resource-listing req)))

(defn api-declaration-json
  [api req]
  (let [datasets (set (data/get-dataset-names))]
    (cond
     (= api "data") (response/json (data-declaration req))
     (contains? datasets api) (response/json (dataset-declaration api req))
     :else (response/status 404 ""))))
