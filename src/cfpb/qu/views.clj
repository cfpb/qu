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
   [cfpb.qu.project :refer [project]]
   [cfpb.qu.util :refer [->int]]
   [cfpb.qu.data :as data]
   [cfpb.qu.query :as query]
   [cfpb.qu.query.select :as select]
   [cfpb.qu.urls :as urls]
   [cheshire.generate :refer [add-encoder encode-str]]
   [clojurewerkz.urly.core :as url]
   [lonocloud.synthread :as ->]
   [liberator.representation :refer [ring-response]])
  (:import [clojurewerkz.urly UrlLike]))

;; Allow for encoding of UrlLike's in JSON.
(add-encoder UrlLike encode-str)

(def footer-info {:qu_version (:version project)
                  :build_number (:build-number project)
                  :build_url (:build-url project)})

(defn json-error
  ([status] (json-error status {}))
  ([status body]
     (response/status
      status
      (response/json body))))

(defn layout-html
  ([content] (layout-html {} content))
  ([resource content] (render-file "templates/layout"
                                   (merge footer-info {:content content
                                                       :resource resource}))))

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
    (try
      (map :select (select/parse select))
      (catch Exception e
        nil))))

(defn- columns-for-view [query slicedef]
  (let [select (:select query)]
    (if (or (str/blank? select)
            (seq (:errors query)))
      (data/slice-columns slicedef)
      (map name (select-fields select)))))

(defn slice-html
  [view-map]
  (render-file "templates/slice" view-map))

(defn concept-name
  "Each dataset has a list of concepts. A concept is a definition of a
  type of data in the dataset. This function retrieves the name
  of the concept."
  [metadata query concept]
  (let [concept (keyword concept)
        concept-name (get-in metadata [:concepts concept :name])]
    (name (or concept-name
              (get-in query [:reverse-aliases concept])
              concept))))

(defn concept-description
  "Each dataset has a list of concepts. A concept is a definition of a
  type of data in the dataset. This function retrieves the description
  of the concept."
  [metadata concept]
  (get-in metadata [:concepts (keyword concept) :description] (name concept)))

(defn format-not-found [format]
  (ring-response
   (response/status
    406
    (response/content-type
     "text/plain"
     (str "Format not found: " format ".")))))

(defmulti index (fn [format _] format))

(defmethod index "text/html" [_ resource]
  (layout-html resource
               (render-file "templates/index" {:datasets (map second (:embedded resource))})))

(defmethod index "application/json" [_ resource]
  (hal/resource->representation resource :json))

(defmethod index "application/xml" [_ resource]
  (hal/resource->representation resource :xml))

(defmethod index :default [format _]
  (format-not-found format))

(defmulti dataset (fn [format _ _] format))

(defmethod dataset "text/html" [_ resource _]
  (let [dataset (get-in resource [:properties :id])]
    (layout-html resource
                 (render-file "templates/dataset"
                              {:resource resource
                               :url (urls/dataset-path dataset)
                               :dataset dataset
                               :slices (->> (:embedded resource)
                                            (filter #(= (first %) "slice"))
                                            (map second))
                               :concepts (->> (:embedded resource)
                                              (filter #(= (first %) "concept"))
                                              (map second))                               
                               :definition (with-out-str (pprint (:properties resource)))}))))

(defmethod dataset "application/json" [_ resource _]
  (hal/resource->representation resource :json))

(defmethod dataset "text/javascript" [_ resource {:keys [callback]}]
  (let [callback (if (str/blank? callback) "callback" callback)]
    (str callback "("
         (hal/resource->representation resource :json)
         ");")))

(defmethod dataset "application/xml" [_ resource _]
  (hal/resource->representation resource :xml))

(defmethod dataset :default [format _ _]
  (format-not-found format))

(defmulti concept (fn [format _ _] format))

(defmethod concept "text/html" [_ resource _]
  (let [properties (:properties resource)
        table (:table properties)
        dataset (:dataset properties)
        concept (:id properties)
        columns (map name (concat [:_id] (keys (:properties properties {}))))]
    (layout-html resource
                 (render-file "templates/concept"
                              {:resource resource
                               :url (urls/concept-path dataset concept)
                               :dataset dataset
                               :concept concept
                               :columns columns
                               :table (data/get-data-table table columns)                               
                               :has-table? (not (empty? table))}))))

(defmethod concept "application/json" [_ resource _]
  (hal/resource->representation resource :json))

(defmethod concept "text/javascript" [_ resource {:keys [callback]}]
  (let [callback (if (str/blank? callback) "callback" callback)]
    (str callback "("
         (hal/resource->representation resource :json)
         ");")))

(defmethod concept "application/xml" [_ resource _]
  (hal/resource->representation resource :xml))

(defmethod concept :default [format _ _]
  (format-not-found format))

(defmulti slice-metadata (fn [format _ _] format))

(defmethod slice-metadata "text/html" [_ resource _]
  (let [properties (:properties resource)
        dataset (:dataset properties)
        slice (:slice properties)
        types (map (fn [[column type]]
                     {:column (name column) :type type})
                   (:types properties))
        references (map (fn [[column data]]
                          {:column (name column) :data data})
                        (:references properties))]
    (layout-html resource
                 (render-file "templates/slice-metadata"
                              {:resource resource
                               :url (urls/slice-metadata-path dataset slice)
                               :dataset dataset
                               :slice slice
                               :dimensions (:dimensions properties)
                               :metrics (:metrics properties)
                               :types types
                               :references references
                               :has-references? (not (empty? references))}))))

(defmethod slice-metadata "application/json" [_ resource _]
  (hal/resource->representation resource :json))

(defmethod slice-metadata "text/javascript" [_ resource {:keys [callback]}]
  (let [callback (if (str/blank? callback) "callback" callback)]
    (str callback "("
         (hal/resource->representation resource :json)
         ");")))

(defmethod slice-metadata "application/xml" [_ resource _]
  (hal/resource->representation resource :xml))

(defmethod slice-metadata :default [format _ _]
  (format-not-found format))


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

(defmulti slice-query (fn [format _ _]
                        format))

(defmethod slice-query "text/html" [_ resource {:keys [query metadata slicedef headers dimensions]}]
  (let [desc (partial concept-name metadata query)
        dataset (get-in resource [:properties :dataset])
        slice (get-in resource [:properties :slice])
        query (get-in resource [:properties :query])
        base-href (urls/slice-path dataset slice)        
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
        columns (columns-for-view query slicedef)
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
        has-data? (> (- end start) 0)
        pagination (create-pagination resource)]
    (layout-html resource
                 (slice-html
                  {:action (str "http://" (headers "host") base-href)
                   :base-href base-href
                   :metadata-href (urls/slice-metadata-path dataset slice)
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
                   :has-data? has-data?
                   :data data}))))

(defmethod slice-query "text/csv" [_ resource {:keys [query slicedef]}]
  (let [table (:table slicedef)
        data (get-in resource [:properties :results])
        columns (columns-for-view query slicedef)
        rows (data/get-data-table data columns)]
    (let [links (reduce conj
                        [{:href (:href resource) :rel "self"}]
                        (:links resource))
          links (map #(str "<" (:href %) ">; rel=" (:rel %)) links)]
      (->> (str (write-csv (vector columns)) (write-csv rows))
           (response/content-type "text/csv; charset=utf-8")
           (response/set-headers {"Link" (str/join ", " links)})
           (ring-response)))))

(defmethod slice-query "application/json" [_ resource _]
  (hal/resource->representation resource :json))

(defmethod slice-query "text/javascript" [_ resource {:keys [callback]}]
  (let [callback (if (str/blank? callback) "callback" callback)]
    (str callback "("
         (hal/resource->representation resource :json)
         ");")))

(defmethod slice-query "application/xml" [_ resource _]
  (hal/resource->representation resource :xml))

(defmethod slice-query :default [format _ _]
  (format-not-found format))
