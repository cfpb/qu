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
   [clojure.data.csv :as csv]
   monger.json
   [halresource.resource :as hal]
   [cfpb.qu.util :refer [->int]]
   [cfpb.qu.data :as data]
   [cfpb.qu.query :as query]
   [cfpb.qu.query.select :as select]
   [cheshire.generate :refer [add-encoder encode-str]]
   [clojurewerkz.urly.core :as url]
   [lonocloud.synthread :as ->])
  (:import [clojurewerkz.urly UrlLike]))

;; Allow for encoding of UrlLike's in JSON.
(add-encoder UrlLike encode-str)

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

(defn- write-csv [data]
  (with-out-str (csv/write-csv *out* data)))

(defn select-fields
  "In API requests, the user can select the columns they want
  returned. If they choose to do this, the columns will be in a
  comma-separated string. This function returns a seq of column names
  from that string."
  [select]
  (if select
    (map :select (select/parse select))))

(defn- columns-for-view [resource slicedef]
  (let [select (get-in resource [:properties :query :select])]
    (if (or (str/blank? select)
            (seq (get-in resource [:properties :errors])))
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
               (render-file "templates/dataset"
                            {:resource resource
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
  [{:key "select"   :label "Select (fields to return)" :placeholder "state,age,population_2010"}
   {:key "group"    :label "Group By"}
   {:key "where"    :label "Where"                     :placeholder "age > 18"}
   {:key "orderBy"  :label "Order By"                  :placeholder "age desc, population_2010"}
   {:key "limit"    :label "Limit (default is 100)"    :placeholder 100}
   {:key "offset"   :label "Offset (default is 0)"     :placeholder 0}
   {:key "callback" :label "Callback for JSONP"        :placeholder "callback"}])

(defn resource-to-href [resource]
  (let [clauses (->> (get-in resource [:properties :query])
                     (into [])
                     (map (fn [[k v]]
                            (str "$" (name k) "=" v))))
        dimensions (->> (get-in resource [:properties :dimensions])
                        (into [])
                        (map (fn [[k v]]
                            (str (name k) "=" v))))
        query (str/join "&" (concat clauses dimensions))]
    (url/mutate-query (:href resource) query)))

(defn- href-for-page [resource page]
  (resource-to-href
   (update-in resource [:properties :query]
              (fn [query]
                (merge query {:page page
                              :offset ""})))))

(defn- create-pagination [resource]
  (if-let [total (get-in resource [:properties :total])]
    (let [window-size 3       
          current-page (or (get-in resource [:properties :page]) 1)
          href (:href resource)
          limit (get-in resource [:properties :query :limit])
          total-pages (+ (quot total limit)
                         (if (zero? (rem total limit)) 0 1))
          window (range (max 1 (- current-page window-size))
                        (inc (min total-pages (+ current-page window-size))))
          in-window? (fn [page]
                       (contains? (set window) page))
          pagination (map #(hash-map :page %
                                     :class (when (= % current-page) "active")
                                     :href (href-for-page resource %))
                          window)]
      (-> pagination
          (conj {:page "Prev"
                 :class (when (<= current-page 1) "disabled")
                 :href (href-for-page resource (dec current-page))})
          (conj {:page "First"
                 :class (when (in-window? 1) "disabled")
                 :href (href-for-page resource 1)})
          (concat [{:page "Next"
                    :class (when (>= current-page total-pages) "disabled")
                    :href (href-for-page resource (inc current-page))}
                   {:page "Last"
                    :class (when (in-window? total-pages) "disabled")
                    :href (href-for-page resource total-pages)}])))
    []))

(defmethod slice "text/html" [_ resource {:keys [metadata slicedef headers dimensions]}]
  (let [desc (partial concept-description metadata)
        dataset (get-in resource [:properties :dataset])
        slice (get-in resource [:properties :slice])
        query (get-in resource [:properties :query])
        action (str "http://" (headers "host")
                    "/data/" dataset
                    "/" slice)
        slice-metadata {:name (get-in slicedef [:info :name])
                        :description (get-in slicedef [:info :description])
                        :dimensions (str/join ", " (:dimensions slicedef))
                        :metrics (str/join ", " (:metrics slicedef))}
        dimensions (map #(hash-map :key %
                                   :name (desc %)
                                   :value (get-in dimensions [(keyword %)]))
                        (:dimensions slicedef))
        clauses (->> clauses
                     (map #(assoc-in % [:value] (get-in resource
                                                        [:properties :query (keyword (:key %))])))
                     (map #(assoc-in % [:errors] (get-in resource
                                                         [:properties :errors (keyword (:key %))]))))
        data (get-in resource [:properties :results])
        columns (columns-for-view resource slicedef)
        data (data/get-data-table data columns)
        columns (map desc columns)
        start (-> (get-in resource [:properties :query :offset])
                  (->int 0)
                  inc)
        end (-> (get-in resource [:properties :size])
                (->int 0)
                (+ start)
                dec)
        total (get-in resource [:properties :total])
        pagination (create-pagination resource)]
    (layout-html resource
                 (slice-html
                  {:action action
                   :dataset dataset
                   :slice slice
                   :metadata slice-metadata
                   :dimensions dimensions
                   :clauses clauses
                   :columns columns
                   :start start
                   :end end
                   :total total
                   :pagination pagination
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

(defmethod slice "text/javascript" [_ resource {:keys [callback]}]
  (let [callback (if (str/blank? callback) "callback" callback)]
    (str callback "("
         (hal/resource->representation resource :json)
         ");")))

(defmethod slice "application/xml" [_ resource _]
  (hal/resource->representation resource :xml))

(defmethod slice :default [format _ _]
  (format-not-found format))
