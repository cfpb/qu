(ns cfpb.qu.views
  "Functions to display resource data in HTML, CSV, and JSON formats."
  (:require
   [taoensso.timbre :as log]
   [clojure
    [string :as str]
    [pprint :refer [pprint]]]
   [compojure
    [response :refer [render]]]
   [stencil.core :refer [render-file]]
   [noir.response :as response]
   [clojure-csv.core :refer [write-csv]]
   monger.json
   [halresource.resource :as hal]
   [cfpb.qu.data :as data]
   [cfpb.qu.query :as query]
   [cfpb.qu.query.select :as select]))

(defn json-error
  ([status] (json-error status {}))
  ([status body]
     (response/status
      status
      (response/json body))))

(defn layout-html
  ([content] (layout-html {} content))
  ([resource content] (render-file "templates/layout"
                                   {:content content
                                    :resource resource})))

(defn not-found-html [message]
  (render-file "templates/404" {:message message}))

(defn select-fields
  "In API requests, the user can select the columns they want
  returned. If they choose to do this, the columns will be in a
  comma-separated string. This function returns a seq of column names
  from that string."
  [select]
  (if select
    (map :select (select/parse select))))

(defn- columns-for-view [resource slicedef]
  (let [select (get-in resource [:properties :select])]
    (if (or (str/blank? select)
            (not (empty? (get-in resource [:properties :errors]))))
      (data/slice-columns slicedef)
      (map name (select-fields select)))))

(defn slice-html
  [view-map]
  (render-file "templates/slice" view-map))

(defn concept-description
  "Each dataset has a list of concepts. A concept is a definition of a
  type of data in the dataset. This function retrieves the description
  of the concept."
  [metadata concept]
  (get-in metadata [:concepts (keyword concept) :description] (name concept)))

(defn format-not-found [format]
  (response/status
   406
   (response/content-type
    "text/plain"
    (str "Format not found: " format "."))))

(defmulti index (fn [format _]
                  format))

(defmethod index "text/html" [_ resource]
  (layout-html resource
               (render-file "templates/index" {:datasets (map second (:embedded resource))})))

(defmethod index "application/json" [_ resource]
  (hal/resource->representation resource :json))

(defmethod index "application/xml" [_ resource]
  (hal/resource->representation resource :xml))

(defmethod index :default [format _]
  (format-not-found format))

(defmulti dataset (fn [format _] format))

(defmethod dataset "text/html" [_ resource]
  (layout-html resource
               (render-file "templates/dataset" {:resource resource
                                                 :dataset (get-in resource [:properties :id])
                                                 :slices (map second (:embedded resource))
                                                 :definition (with-out-str (pprint (:properties resource)))})))

(defmethod dataset "application/json" [_ resource]
  (hal/resource->representation resource :json))

(defmethod dataset "application/xml" [_ resource]
  (hal/resource->representation resource :xml))

(defmethod dataset :default [format _]
  (format-not-found format))

(defmulti slice (fn [format _ _]
                  format))

(def clauses
  [{:key "select"  :label "Select (fields to return)" :placeholder "state,age,population_2010"}
   {:key "group"   :label "Group By"}
   {:key "where"   :label "Where"                     :placeholder "age > 18"}
   {:key "orderBy" :label "Order By"                  :placeholder "age desc, population_2010"}
   {:key "limit"   :label "Limit (default is 100)"    :placeholder 100}
   {:key "offset"  :label "Offset (default is 0)"     :placeholder 0}])

(defmethod slice "text/html" [_ resource {:keys [metadata slicedef headers dimensions]}]
  (let [desc (partial concept-description metadata)
        dataset (get-in resource [:properties :dataset])
        slice (get-in resource [:properties :slice])
        action (str "http://" (headers "host")
                    "/data/" dataset
                    "/" slice)
        slice-metadata {:dimensions (str/join ", " (:dimensions slicedef))
                        :metrics (str/join ", " (:metrics slicedef))}
        dimensions (map #(hash-map :key %
                                   :name (desc %)
                                   :value (get-in dimensions [(keyword %)]))
                        (:dimensions slicedef))
        clauses (->> clauses
                     (map #(assoc-in % [:value] (get-in resource
                                                        [:properties (keyword (:key %))])))
                     (map #(assoc-in % [:errors] (get-in resource
                                                         [:properties :errors (keyword (:key %))]))))
        data (get-in resource [:properties :results])
        columns (columns-for-view resource slicedef)
        data (data/get-data-table data columns)
        columns (map desc columns)]
    (layout-html resource
                 (slice-html
                  {:action action
                   :dataset dataset
                   :slice slice
                   :metadata slice-metadata
                   :dimensions dimensions
                   :clauses clauses
                   :columns columns
                   :data data}))))

(defmethod slice "text/csv" [_ resource {:keys [slicedef]}]
  (let [table (:table slicedef)
        data (get-in resource [:properties :results])
        columns (columns-for-view resource slicedef)
        rows (data/get-data-table data columns)]
    (let [links (reduce conj
                        [{:href (:href resource) :rel "self"}]
                        (:links resource))
          links (map #(str "<" (:href %) ">; rel=" (:rel %)) links)]
      (->> (str (write-csv (vector columns)) (write-csv rows))
           (response/content-type "text/csv; charset=utf-8")
           (response/set-headers {"Link" (str/join ", " links)})))))

(defmethod slice "application/json" [_ resource _]
  (hal/resource->representation resource :json))

(defmethod slice "application/xml" [_ resource _]
  (hal/resource->representation resource :xml))

(defmethod slice :default [format _ _]
  (format-not-found format))
