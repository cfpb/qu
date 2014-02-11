(ns cfpb.qu.views
  "Functions to display resource data in HTML, CSV, and JSON formats."
  (:require
   [taoensso.timbre :as log]
   [clojure
    [string :as str]
    [pprint :refer [pprint]]]
   [clojure.java.io :as io]
   [compojure
    [response :refer [render]]]
   [antlers.core :as antlers]
   [ring.util.response :as response]
   [clojure.data.csv :as csv]
   [clojure.data.xml :as xml]
   monger.json
   [halresource.resource :as hal]
   [cfpb.qu.project :refer [project]]
   [cfpb.qu.env :refer [env]]
   [cfpb.qu.util :refer :all]
   [cfpb.qu.data :as data]
   [cfpb.qu.query :as query]
   [cfpb.qu.query.select :as select]
   [cfpb.qu.urls :as urls]
   [cheshire.generate :refer [add-encoder encode-str]]
   [cheshire.core :as json]
   [clojurewerkz.urly.core :as url]
   [lonocloud.synthread :as ->]
   [liberator.representation :refer [ring-response]]
   [org.httpkit.server :refer :all])
  (:import [clojurewerkz.urly UrlLike]
           [java.io ByteArrayInputStream]
           [java.nio ByteBuffer]))

;; Allow for encoding of UrlLike's in JSON.
(add-encoder UrlLike encode-str)

(def ^:dynamic *min-records-to-stream* 1000)
(def ^:dynamic *stream-size* 1024)

(def layout-info {:qu_version (@project :version)
                  :build_number (@project :build-number)
                  :build_url (@project :build-url)
                  :api_name (env :api-name)
                  :dev_mode (env :dev)})

(defn json-error
  ([status] (json-error status {}))
  ([status body]
     (response/status
      (json-response body)
      status)))

(defn layout-html
  ([content] (layout-html {} content))
  ([resource content]
     (antlers/render-file "templates/layout"
                  (merge layout-info {:content content
                                      :resource resource}))))

(defn not-found-html [message]
  (antlers/render-file "templates/404" {:message message}))

(defn error-html
  ([] (error-html 500))
  ([status]
     (-> (response/response (layout-html (antlers/render-file "templates/500" {})))
         (response/status status)
         (response/content-type "text/html"))))

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
  (antlers/render-file "templates/slice" view-map))

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
   (-> (str "Format not found: " format ".")
       (response/response)
       (response/status 406)
       (response/content-type "text/plain"))))

(defmulti index (fn [format _] format))

(defmethod index "text/html" [_ resource]
  (layout-html resource
               (antlers/render-file "templates/index" {:api_name (env :api-name)
                                               :datasets (map second (:embedded resource))})))

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
                 (antlers/render-file "templates/dataset"
                              {:resource resource
                               :url (urls/dataset-path :dataset dataset)
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
        columns (map name (keys (:properties properties {})))]
    (layout-html resource
                 (antlers/render-file "templates/concept"
                              {:resource resource
                               :url (urls/concept-path :dataset dataset :concept concept)
                               :dataset dataset
                               :concept concept
                               :columns columns
                               :table (data/get-data-table (:data table) columns)
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
                 (antlers/render-file "templates/slice-metadata"
                              {:resource resource
                               :url (urls/slice-metadata-path :dataset dataset :slice slice)
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
        query (str/join "&" clauses)]
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
          total-pages (if (zero? limit)
                        1
                        (+ (quot total limit)
                           (if (zero? (rem total limit)) 0 1)))
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

(defmulti slice-query (fn [format _ _] format))

(defmethod slice-query "text/html"
  [_ resource {:keys [request query metadata slicedef headers]}]
  (let [desc (partial concept-name metadata query)
        dataset (get-in resource [:properties :dataset])
        slice (get-in resource [:properties :slice])
        query (get-in resource [:properties :query])
        base-href (urls/slice-query-path :dataset dataset :slice slice)
        dimensions (:dimensions slicedef)
        metrics (:metrics slicedef)
        sample-dimension (first (sort #(< (count %1) (count %2)) dimensions))
        sample-metric (first (sort #(< (count %1) (count %2)) metrics))
        slice-metadata {:name (get-in slicedef [:info :name])
                        :description (get-in slicedef [:info :description])
                        :dimensions (str/join ", " dimensions)
                        :metrics (str/join ", " metrics)}
        dimension-form-data (map #(hash-map :key %
                                            :name (desc %)
                                            :value (get-in dimensions [(keyword %)]))
                                 dimensions)
        placeholders {:select (str/join ", " (vector sample-dimension sample-metric))
                      :where (str sample-metric " > 10")
                      :orderBy (str sample-dimension ", " sample-metric " DESC")}
        clauses (->> clauses
                     (map #(assoc-in % [:value] (get-in resource
                                                        [:properties :query (keyword (:key %))])))
                     (map #(assoc-in % [:placeholder]
                                     ((keyword (:key %)) placeholders (:placeholder %))))
                     (map #(assoc-in % [:errors] (get-in resource
                                                         [:properties :errors (keyword (:key %))]))))
        data (take 100 (get-in resource [:properties :results]))
        columns (columns-for-view query slicedef)
        data (data/get-data-table data columns)
        columns (map desc columns)
        data-size (->int (get-in resource [:properties :size]) 0)
        start (-> (get-in resource [:properties :query :offset])
                  (->int 0)
                  inc)
        end (-> data-size
                (+ start)
                dec)
        total (get-in resource [:properties :total])
        has-data? (pos? (- end start))
        has-more-data? (> data-size 100)
        pagination (create-pagination resource)
        computing (get-in resource [:properties :computing])
        computing? (->bool computing)]
    (response/content-type
     (response/response
      (layout-html resource
                   (slice-html
                    {:even? even?
                     :odd? odd?
                     :action (str (base-url request) base-href)
                     :base-href base-href
                     :metadata-href (urls/slice-metadata-path :dataset dataset :slice slice)
                     :dataset dataset
                     :slice slice
                     :metadata slice-metadata
                     :dimensions dimension-form-data
                     :clauses clauses
                     :columns columns
                     :start start
                     :end end
                     :total total
                     :pagination pagination
                     :has-data? has-data?
                     :has-more-data? has-more-data?
                     :computing? computing?
                     :computing computing
                     :data data})))
     "text/html;charset=UTF-8")))

(defn- should-stream?
  [resource]
  (> (->int (get-in resource [:properties :size]) 0)
     *min-records-to-stream*))

(defn- ch->outputstream [ch]
  (proxy [java.io.OutputStream] []
    (close []
       (close ch))
    (write
      ([^bytes bs] ;; bytes of byte
         (let [bb (ByteBuffer/wrap bs)]
           (send! ch bb false)))
      ([^bytes bs off len]
         (let [bb (ByteBuffer/wrap bs off len)]
           (send! ch bb false))))))

(defn- ch->writer [ch]
  (io/writer (ch->outputstream ch)))

(defn future-stream
  [ch write-fn data]
  (future (write-fn ch data)))

(defn stream-data
  [request response data write-fn]
  (with-channel request ch
    (log/info "Channel opened")
    (send! ch response false)

    (let [ch-future (future-stream ch write-fn data)]
      (on-close ch (fn [status]
                     (log/info "Channel closed" status)
                     (if-not (= status :server-close)
                       (future-cancel ch-future))))
      (try
        (deref ch-future)
        (catch java.util.concurrent.CancellationException ex
          (log/info "Channel closed early: future cancelled")))))
  response)

(defn- stream-slice-query-csv
  [request response data]
  (stream-data request response data
               (fn [ch data]
                 (with-open [writer (ch->writer ch)]
                   (csv/write-csv writer data :quote? (constantly true)))
                 (close ch))))

(defn- stream-slice-query-json
  [request response resource]
  (let [resource (hal/json-representation resource)]
    (stream-data request response resource
                 (fn [ch resource]
                   (with-open [writer (ch->writer ch)]
                     (json/generate-stream resource writer))
                   (close ch)))))

(defn- stream-slice-query-jsonp
  [request response resource callback]
  (let [resource (hal/json-representation resource)]
    (stream-data request response resource
                 (fn [ch resource]
                   (send! ch (assoc response :body (str callback "(")) false)
                   (with-open [writer (ch->writer ch)]
                     (json/generate-stream resource writer)
                     (send! ch ");" true))))))

(defn- stream-slice-query-xml
  [request response resource]
  (stream-data request response resource
               (fn [ch resource]
                 (with-open [writer (ch->writer ch)]
                   (xml/emit resource writer))
                 (close ch))))

(defmethod slice-query "text/csv" [_ {:keys [properties href links] :as resource} {:keys [request query slicedef]}]
  (let [table (:table slicedef)
        computing (:computing properties)
        data (:results properties)
        columns (columns-for-view query slicedef)
        rows (data/get-data-table data columns)
        links (reduce conj
                      [{:href href :rel "self"}]
                      links)
        links (map #(str "<" (:href %) ">; rel=" (:rel %)) links)
        respond #(-> (response/response %)
                     (response/content-type "text/csv;charset=UTF-8")
                     (response/header "Link" (str/join ", " links))
                     (response/header "X-Computing" (->bool computing)))]
    (if (query/valid? query)
      (if (should-stream? resource)
        (stream-slice-query-csv request (respond (write-csv (vector columns))) rows)
        (respond (str (write-csv (vector columns)) (write-csv rows))))
      (response/content-type (response/response "") "text/plain"))))

(defmethod slice-query "application/json"
  [_ resource {:keys [request]}]
  (let [response (response/content-type {} "application/json;charset=UTF-8")]
    (if (should-stream? resource)
      (stream-slice-query-json request response resource)
      (assoc response :body (hal/resource->representation resource :json)))))

(defmethod slice-query "text/javascript" [_ resource {:keys [request callback]}]
  (let [response (response/content-type {} "text/javascript;charset=UTF-8")
        callback (if (str/blank? callback) "callback" callback)]
    (if (should-stream? resource)
      (stream-slice-query-jsonp request response resource callback)
      (assoc response :body (str callback "(" (hal/resource->representation resource :json) ");")))))

(defmethod slice-query "application/xml" [_ resource {:keys [request]}]
  (let [response (response/content-type {} "application/xml;charset=UTF-8")
        xml-resource (hal/xml-representation resource)]
    (if (should-stream? resource)
      (stream-slice-query-xml request response xml-resource)
      (assoc response :body (xml/emit-str xml-resource)))))

(defmethod slice-query :default [format _ _]
  (format-not-found format))
