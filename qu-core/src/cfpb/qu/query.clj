(ns cfpb.qu.query
  "Functions to build and execute queries."
  (:require [monger.query :as q]
            [lonocloud.synthread :as ->]
            [taoensso.timbre :as log]
            [cfpb.qu.metrics :as metrics]
            [cfpb.qu.data :as data]
            [cfpb.qu.util :refer [->int ->num]]            
            [cfpb.qu.query.mongo :as mongo]
            [cfpb.qu.query.select :as select]
            [cfpb.qu.query.validation :as validation]
            [cfpb.qu.cache :refer [query-to-key]]))

(defrecord
    ^{:doc "This record contains all the information about a query.
    Much of this comes from requests to the system. The rest is
    accreted throughout the query parsing and verification process."}
    Query
  [select group where orderBy limit offset callback
   mongo errors
   dataset slice metadata slicedef])

(def default-limit 100)
(def default-offset 0)
(def allowed-clauses
  #{:$select :$where :$orderBy :$group :$limit :$offset :$callback :$page :$perPage})

(defn valid?
  [query]
  (validation/valid? query))

(defn parse-params
  "Given a slice definition and the request parameters, convert those
parameters into something we can use. Specifically, pull out the clauses."
  [params]
  (into {} (filter (fn [[key value]]
                     (and
                      (not= value "")
                      (allowed-clauses key))) params)))

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

(defn is-aggregation? [query]
  (:group query false))

(defn- resolve-limit-and-offset [{:keys [limit offset page perPage] :as query}]
  (let [limit (or (->int limit nil)
                  (->int perPage nil)
                  default-limit)
        offset (->int offset nil)
        page (->int page (if (zero? limit)
                           nil
                           (when (and offset
                                      (zero? (mod offset limit)))
                             (inc (/ offset limit)))))]
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
  "Prepare the query for execution."
  [query]
  (-> query
      validation/validate
      resolve-limit-and-offset
      mongo/process
      (assoc :prepared? true)))

(defn execute
  "Execute the query against the provided collection."
  [{:keys [dataset slice] :as query}]

  (metrics/with-timing "queries.execute"
    (let [_ (log/info "Execute query" (str (into {} (dissoc query :metadata :slicedef))))
          query (if (:prepared? query) query (prepare query))]
      (cond
        (not (valid? query))
        (do
          (metrics/increment "queries.invalid")
          [])

        (is-aggregation? query)
        (let [agg (mongo-aggregation query)]
          (data/get-aggregation dataset slice agg))

        :default
        (data/get-find dataset slice (mongo-find query))))))

(defn params->Query
  "Convert params from a web request plus a dataset definition and a
  slice name into a Query record."
  [params metadata slice]
  (let [slicedef (get-in metadata [:slices (keyword slice)])
        dataset (:name metadata)
        clauses (parse-params params)
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
                 :metadata metadata
                 :dataset dataset
                 :slice slice
                 :slicedef slicedef})))

(defn make-query
  "Convenience function to quickly make a query for testing at the
  REPL."
  [{:keys [dataset slice] :as q}]
  {:pre [(every? #(not (nil? %)) [dataset slice])]}
  (let [metadata (data/get-metadata dataset)
        slicedef (get-in metadata [:slices (keyword slice)])]
    (-> q
        (assoc :metadata metadata :slicedef slicedef)
        (map->Query))))
