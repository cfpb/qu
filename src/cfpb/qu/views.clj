(ns cfpb.qu.views
  "Functions to display resource data in HTML, CSV, and JSON formats."
  (:require
   [clojure.java.io :as io]
   [clojure
    [string :as str]
    [pprint :refer [pprint]]]
   [compojure
    [route :as route]
    [response :refer [render]]]
   [clojure-csv.core :refer [write-csv]]
   [cheshire.core :as json]
   [monger
    [core :as mongo :refer [get-db with-db]]
    [collection :as coll]
    json]
   [net.cgrand.enlive-html
    :as html
    :refer [deftemplate defsnippet]]
   [ring.util.response :refer [content-type]]
   ring.middleware.content-type
   [cfpb.qu.data :as data]))

(deftemplate layout-html "templates/layout.html"
  [content]

  [:div#content]
  (html/content content))

(defsnippet index-html "templates/index.html" [:#content]
  [datasets]

  [:ul#dataset-list :li]
  (html/clone-for [dataset datasets]
                  [:a]
                  (html/do->
                   (html/content (str (get-in dataset [:info :name])))
                   (html/set-attr :href (str "/data/" (:name dataset))))

                  [:small]
                  (html/content (get-in dataset [:info :description]))))

(defsnippet not-found-html "templates/404.html" [:#content]
  [msg]

  [:.message]
  (html/content msg))

(defsnippet dataset-html "templates/dataset.html" [:#content]
  [dataset metadata]

  [:h1 html/any-node]
  (html/replace-vars {:dataset dataset})

  [:h1 :a.index]
  (html/set-attr :href "/data")

  [:ul#slices :li]
  (html/clone-for [slice (map name (keys (:slices metadata)))]
                  [:a]
                  (html/do->
                   (html/content slice)
                   (html/set-attr :href (str "/data/" dataset "/" slice))))

  [:pre.definition]
  (html/content (with-out-str (pprint metadata))))

(defn- columns-for-view [slice-def params]
  (let [select (:$select params)]
    (if (and select
             (not= select ""))
      (data/select-fields select)
      (data/slice-columns slice-def))))

(defsnippet slice-html "templates/slice.html" [:#content]
  [params action dataset metadata slice-name slice-def columns data]

  [:form#query-form]
  (html/do->
   (html/set-attr :action action)
   (html/set-attr :data-href action))

  [:h1 html/any-node]
  (html/replace-vars {:dataset dataset})

  [:h1 :a.index]
  (html/set-attr :href "/data")

  [:h1 :a.dataset]
  (html/set-attr :href (str "/data/" dataset))

  [:.metadata html/any-node]
  (html/replace-vars {:dimensions (str/join ", " (:dimensions slice-def))
                      :metrics (str/join ", " (:metrics slice-def))})

  [:.dimension-fields :.dimension-field]
  (html/clone-for [dimension (:dimensions slice-def)]
                  [:label]
                  (html/do->
                   (html/content (data/concept-description metadata dimension))
                   (html/set-attr :for (str "field-" dimension)))

                  [:input]
                  (html/do->
                   (html/set-attr :name dimension)
                   (html/set-attr :id (str "field-" dimension))
                   (html/set-attr :value (or (params (keyword dimension))
                                             (params dimension)))))

  [:#query-results :thead :tr]
  (html/content (html/html
                 (for [column columns]
                   [:th (data/concept-description metadata column)])))

  [:#query-results :tbody :tr]
  (html/clone-for [row data]
                  (html/content (html/html
                                 (for [value row]
                                   [:td value])))))

(defmulti slice (fn [format _ _]
                  format))

(defmethod slice "text/html" [_ data {:keys [dataset slice-def params headers]}]
  (let [metadata (data/get-metadata dataset)
        slice-name (:slice params)
        action (str "http://" (headers "host") "/data/" dataset "/" slice-name)
        columns (columns-for-view slice-def params)
        data (data/get-data-table data columns)]

    (apply str (layout-html (slice-html params
                                        action
                                        dataset
                                        metadata
                                        slice-name
                                        slice-def
                                        columns
                                        data)))))

(defmethod slice "application/json" [_ data _]
  (json/generate-string data))

(defmethod slice "text/csv" [_ data {:keys [slice-def params]}]
  (let [table (:table slice-def)
        columns (columns-for-view slice-def params)
        rows (data/get-data-table data columns)]
    (str (write-csv (vector columns)) (write-csv rows))))

(defmethod slice :default [format _ _]
  (route/not-found (str "format not found: " format)))
