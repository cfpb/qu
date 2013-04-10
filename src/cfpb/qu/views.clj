(ns cfpb.qu.views
  "Functions to display resource data in HTML, CSV, and JSON formats."
  (:require
   [clojure.java.io :as io]
   [clojure.tools.logging :refer [info error]]
   [clojure
    [string :as str]
    [pprint :refer [pprint]]]
   [compojure
    [route :as route]
    [response :refer [render]]]
   [noir.validation :as valid]
   [clojure-csv.core :refer [write-csv]]
   [monger
    [core :as mongo :refer [get-db with-db]]
    [collection :as coll]
    json]
   [stencil.core :refer [render-file]]
   [ring.util.response :refer [content-type]]
   ring.middleware.content-type
   [noir.response :as response]
   [cfpb.qu.data :as data]
   [cfpb.qu.query :as query]
   [cfpb.qu.query.select :as select]))

(defn json-error
  ([status] (json-error status {}))
  ([status body]
     (response/status
      status
      (response/json body))))

(defn layout-html [content]
  (render-file "templates/layout"
               {:content content}))

(defn index-html [datasets]
  (render-file "templates/index" {:datasets datasets}))

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
    (->> (select/parse select)
         (map :select))))

(defn- columns-for-view [slice-def params]
  (let [select (:$select params)]
    (if (and select
             (not= select ""))
      (select-fields select)
      (data/slice-columns slice-def))))

(def clauses
  [{:key "select" :label "Select (fields to return)" :placeholder "state,age,population_2010"}
   {:key "group" :label "Group By"}
   {:key "where" :label "Where" :placeholder "age > 18"}
   {:key "orderBy" :label "Order By" :placeholder "age desc, population_2010"}
   {:key "limit" :label "Limit (default is 100)" :placeholder 100}
   {:key "offset" :label "Offset (default is 0)" :placeholder 0}])

(defn slice-html
  [slice params action dataset metadata slice-def columns data]
  
  (render-file "templates/slice"
               {:action action
                :dataset dataset
                :slice slice
                :metadata {:dimensions
                           (str/join ", " (:dimensions slice-def))
                           :metrics
                           (str/join ", " (:metrics slice-def))}
                :dimensions (map #(hash-map :key %
                                            :name (data/concept-description metadata %)
                                            :value (params (keyword %)))
                                 (:dimensions slice-def))
                ;; TODO errors
                :clauses (map #(assoc-in % [:value] (params (keyword (str "$" (:key %)))))
                              clauses)
                :columns (map #(name (data/concept-description metadata %)) columns)
                :data data}))

(defmulti slice (fn [format _ _]
                  format))

(defmethod slice "text/html" [_ data {:keys [dataset slice-def params headers]}]
  (let [metadata (data/get-metadata dataset)
        slice-name (:slice params)
        action (str "http://" (headers "host") "/data/" dataset "/" slice-name)
        columns (columns-for-view slice-def params)
        data (data/get-data-table data columns)]

    (apply str (layout-html (slice-html slice-name
                                        params
                                        action
                                        dataset
                                        metadata
                                        slice-def
                                        columns
                                        data)))))

(defmethod slice "application/json" [_ data _]
  (response/json data))

(defmethod slice "text/csv" [_ data {:keys [slice-def params]}]
  (let [table (:table slice-def)
        columns (columns-for-view slice-def params)
        rows (data/get-data-table data columns)]
    (response/content-type
     "text/csv; charset=utf-8"
     (str (write-csv (vector columns)) (write-csv rows)))))

(defmethod slice :default [format _ _]
  (response/status
   406
   (response/content-type
    "text/plain"
    (str "Format not found: " format
         ". Valid formats are application/json, text/csv, and text/html."))))
