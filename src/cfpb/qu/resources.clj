(ns cfpb.qu.resources
  "RESTful resources for datasets and slices. Uses
Liberator (http://clojure-liberator.github.com/) to handle exposing
the resources.

In Liberator, returning a map from a function adds that map to the
context passed to subsequent functions. We use that heavily in exists?
functions to return the resource that will be presented later."
  (:require
   [clojure.string :as str]
   [liberator.core :refer [defresource request-method-in]]
   [monger.core :as mongo]
   [validateur.validation :as valid]
   [protoflex.parse :refer [parse]]
   [cfpb.qu.data :as data]
   [cfpb.qu.views :as views]
   [cfpb.qu.where.parse-fns :refer [where-expr]]))

(defn index [_]
  (views/layout-html
   (views/index-html
    (data/get-datasets))))

(defn not-found [msg]
  (views/layout-html
   (views/not-found-html msg)))

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

(defn- cast-value [value type]
    (case type
      "integer" (Integer/parseInt value)
      value))

(defn- cast-dimensions
  "Given a slice definition and a set of dimensions from the request,
cast the requested dimensions into the right type for comparison when
querying the database."
  [slice-def dimensions]
  (into {}
        (for [[dimension value] dimensions]
          (vector dimension (cast-value
                             value
                             (get-in slice-def [:types dimension]))))))

(def allowed-clauses #{:$select :$where :$orderBy :$group :$limit :$offset})

(defn parse-params
  "Given a slice definition and the request parameters, convert those
parameters into something we can use. Specifically, pull out the
dimensions and clauses and cast the dimension values into something we
can query with."
  [slice-def params]
  (let [dimensions (set (:dimensions slice-def))]
    {:dimensions (->> (into {} (filter (fn [[key value]]
                                         (and
                                          (not= value "")
                                          (dimensions (name key)))) params))
                      (cast-dimensions slice-def))
     :clauses (into {} (->> params
                            (map (fn [[key value]]
                                   [(keyword key) value]))
                            (filter (fn [[key value]]
                                      (and
                                       (not= value "")
                                       (allowed-clauses key))))))}))

(defn- parses-correctly [key parsefn & {:keys [allow-nil] :or {allow-nil true}}]
  (fn [m]
    (let [input (m key)]
      (try        
        (if (or (and allow-nil (str/blank? input))
                (parse parsefn key))
          [true #{}]
          [false {key #{"does not parse correctly"}}])
        (catch Exception e
          [false {key #{"does not parse correctly"}}])))))

(defresource
  ^{:doc "Resource for an individual slice."}
  slice
  :available-media-types ["text/html" "text/csv" "application/json"]
  :method-allowed? (request-method-in :get)
  :malformed? (fn [{:keys [request]}]
                (let [params (:params request)
                      validator (valid/validation-set
                                 (parses-correctly "$where" where-expr))]
                  (valid/invalid? slice-params-validator params)))
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
                   (let [parsed-params (parse-params slice-def params)
                         data (data/get-data slice-def parsed-params)]
                     (views/slice (:media-type representation)
                                  data
                                  view-map))))))
