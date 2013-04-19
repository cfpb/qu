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
   [ring.util.response :refer [content-type]]
   ring.middleware.content-type
   [noir.response :as response]
   [clojure-csv.core :refer [write-csv]]
   monger.json
   [cfpb.qu.data :as data]
   [cfpb.qu.query :as query]
   [cfpb.qu.query.select :as select]
   [cfpb.qu.hal :as hal]))

(defn json-error
  ([status] (json-error status {}))
  ([status body]
     (response/status
      status
      (response/json body))))

(defn layout-html [content]
  (render-file "templates/layout"
               {:content content}))

(defn not-found-html [message]
  (render-file "templates/404" {:message message}))

(defn dataset-html [dataset metadata]
  (let [slices (map name (keys (:slices metadata)))]
    (render-file "templates/dataset"
                 {:dataset dataset
                  :slices slices
                  :metadata (with-out-str (pprint metadata))})))

(defn select-fields
  "In API requests, the user can select the columns they want
  returned. If they choose to do this, the columns will be in a
  comma-separated string. This function returns a seq of column names
  from that string."
  [select]
  (if select
    (map :select (select/parse select))))

(defn- columns-for-view [query slicedef]
  (let [select (:select query)]
    (if (or (str/blank? select)
            (not (empty? (:errors query))))
      (data/slice-columns slicedef)
      (map name (select-fields select)))))

(defn slice-html
  [view-map]
  (render-file "templates/slice" view-map))

(def clauses
  [{:key "select"  :label "Select (fields to return)" :placeholder "state,age,population_2010"}
   {:key "group"   :label "Group By"}
   {:key "where"   :label "Where"                     :placeholder "age > 18"}
   {:key "orderBy" :label "Order By"                  :placeholder "age desc, population_2010"}
   {:key "limit"   :label "Limit (default is 100)"    :placeholder 100}
   {:key "offset"  :label "Offset (default is 0)"     :placeholder 0}])

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
  (layout-html
   (render-file "templates/index" {:datasets (map second (:embedded resource))})))

(defmethod index "application/json" [_ resource]
  (hal/Resource->representation resource :json))

(defmethod index "application/xml" [_ resource]
  (hal/Resource->representation resource :xml))

(defmethod index :default [format _]
  (format-not-found format))

(defmulti dataset (fn [format & _] format))

(defmethod dataset "application/json" [_ resource]
  (hal/Resource->representation resource :json))

(defmethod dataset "application/xml" [_ resource]
  (hal/Resource->representation resource :xml))

(defmethod dataset :default [format _]
  (format-not-found format))

(defmulti slice (fn [format _ _]
                  format))

(defmethod slice "text/html" [_ query {:keys [dataset slicedef params headers]}]
  (let [desc (partial concept-description (data/get-metadata dataset))
        slicename (:slice params)
        action (str "http://" (headers "host") "/data/" dataset "/" slicename)
        slice-metadata {:dimensions (str/join ", " (:dimensions slicedef))
                        :metrics (str/join ", " (:metrics slicedef))}
        dimensions (map #(hash-map :key %
                                   :name (desc %)
                                   :value (get-in query [:dimensions (keyword %)]))
                        (:dimensions slicedef))
        clauses (->> clauses
                     (map #(assoc-in % [:value] (get-in query [(keyword (:key %))])))
                     (map #(assoc-in % [:errors] (get-in query [:errors (keyword (:key %))]))))
        data (:result query)
        columns (columns-for-view query slicedef)
        data (data/get-data-table data columns)
        columns (map desc columns)]
    (apply str (layout-html (slice-html
                             {:action action
                              :dataset dataset
                              :slice slicename
                              :metadata slice-metadata
                              :dimensions dimensions
                              :clauses clauses
                              :columns columns
                              :data data})))))

(defmethod slice "application/json" [_ query _]
  (response/json (:result query)))

(defmethod slice "text/csv" [_ query {:keys [slicedef]}]
  (let [table (:table slicedef)
        data (:result query)
        columns (columns-for-view query slicedef)
        rows (data/get-data-table data columns)]
    (response/content-type
     "text/csv; charset=utf-8"
     (str (write-csv (vector columns)) (write-csv rows)))))

(defmethod slice :default [format _ _]
  (format-not-found format))
