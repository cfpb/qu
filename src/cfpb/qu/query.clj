(ns cfpb.qu.query
  (:require [clojure.string :as str]
            [monger.query :as q]
            [monger.collection :as coll]
            [cfpb.qu.data :as data]
            [cfpb.qu.query.mongo :as mongo]
            [cfpb.qu.query.select :as select]
            [cfpb.qu.query.validation :as validation]
            [cfpb.qu.query.cache :refer [query-to-key]]
            [cfpb.qu.util :refer [->int ->num]]
            [lonocloud.synthread :as ->]
            [taoensso.timbre :as log]
            [clj-statsd :as sd]))

(def default-limit 100)
(def default-offset 0)

(declare parse-params mongo-find mongo-aggregation)

(defrecord Query [select group where orderBy limit offset callback mongo errors slicedef])
(def allowed-clauses #{:$select :$where :$orderBy :$group :$limit :$offset :$callback :$page :$perPage})

;; TODO refactor and move this and other validations into separate namespace
(defn valid? [query]
  (or (not (:errors query))
      (zero? (reduce + (map count (:errors query))))))

(defn params->Query
  "Convert params from a web request plus a dataset definition and a slice name into a Query record."
  [params metadata slice]
  (let [slicedef (get-in metadata [:slices (keyword slice)])
        dataset (:name metadata)
        {:keys [dimensions clauses]} (parse-params params slicedef)
        {select :$select
         group :$group
         orderBy :$orderBy
         where :$where
         limit :$limit
         offset :$offset
         page :$page
         perPage :$perPage
         callback :$callback} clauses]
    (map->Query {:select select
                 :group group
                 :where where
                 :limit limit
                 :offset offset
                 :page page
                 :perPage perPage
                 :orderBy orderBy
                 :callback callback
                 :dimensions dimensions
                 :metadata metadata
                 :dataset dataset
                 :slice slice
                 :slicedef slicedef})))

(defn is-aggregation? [query]
  (:group query false))

(defn- resolve-limit-and-offset [{:keys [limit offset page perPage] :as query}]
  (let [limit (or (->int limit nil)
                  (->int perPage nil)
                  default-limit)
        limit (if (zero? limit)
                default-limit
                limit)
        offset (->int offset nil)
        page (->int page (when (and offset
                                    (zero? (mod offset limit)))
                           (inc (/ offset limit))))]
    (cond
     page (merge query {:offset (-> page
                                    dec
                                    (* limit))
                        :limit limit
                        :page page})
     offset (merge query {:offset offset
                          :limit limit
                          :page page})
     :default (merge query {:offset default-offset
                            :limit limit
                            :page 1}))))

(defn prepare
  "Prepare the query for execution"
  [query]
  (-> query
      validation/validate
      resolve-limit-and-offset
      mongo/process))

(defn execute
  "Execute the query against the provided collection."
  [{:keys [dataset slice] :as query}]

  (sd/with-timing "qu.queries.execute"
    (let [_ (log/info "Execute query" (str (into {} (dissoc query :metadata :slicedef))))
          query (prepare query)]
      (assoc query :result
             (cond
              (seq (:errors query))
               (do
                 (sd/increment "qu.queries.invalid")
                 [])

              (is-aggregation? query)
              (let [agg (mongo-aggregation query)]
                (data/get-aggregation dataset slice agg))

              :default
              (data/get-find dataset slice (mongo-find query)))))))

(defn- cast-value [value type]
  (case type
    "integer" (->int value)
    "number" (->num value)
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

(defn parse-params
  "Given a slice definition and the request parameters, convert those
parameters into something we can use. Specifically, pull out the
dimensions and clauses and cast the dimension values into something we
can query with."
  [params slice]
  (let [dimensions (set (:dimensions slice))]
    {:dimensions (->> (into {} (filter (fn [[key value]]
                                         (and
                                          (not= value "")
                                          (dimensions (name key)))) params))
                      (cast-dimensions slice))
     :clauses (into {} (filter (fn [[key value]]
                                 (and
                                  (not= value "")
                                  (allowed-clauses key))) params))}))

(defn mongo-find
  "Create a Mongo find map from the query."
  [query]
  (let [mongo (q/partial-query
               (q/find (get-in query [:mongo :match]))
               (q/limit (->int (:limit query) default-limit))
               (q/skip (->int (:offset query) default-offset))
               (q/sort (get-in query [:mongo :sort] {}))
               (q/fields (or (get-in query [:mongo :project]) {})))]
    mongo))

(defn mongo-aggregation
  "Create a Mongo map-reduce aggregation map from the query."
  [{:keys [dataset slice mongo] :as query}]
  (let [filter (:match mongo {})
        fields (get-in mongo [:project :fields])
        aggregations (get-in mongo [:project :aggregations])
        a-query (dissoc query :metadata :slicedef)
        to-collection (query-to-key query)]
    {:query a-query
     :dataset dataset
     :from slice
     :to to-collection
     :group (:group mongo)
     :aggregations aggregations
     :filter filter
     :fields fields
     :sort (:sort mongo)
     :limit (->int (:limit query))
     :offset (->int (:offset query))
     :slicedef (:slicedef query)}))

