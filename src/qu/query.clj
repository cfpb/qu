(ns qu.query
  "Functions to build and execute queries."
  (:require [clojure.string :as str]
            [monger.query :as q]
            [qu.cache :refer [query-to-key]]
            [qu.data :as data]
            [qu.metrics :as metrics]
            [qu.query.mongo :as mongo]
            [qu.query.select :as select]
            [qu.query.validation :as validation]
            [qu.util :refer [->int]]
            [taoensso.timbre :as log]))

(defrecord ^{:doc "This record contains all the information about a
    query.  Much of this comes from requests to the system. The rest
    is accreted throughout the query parsing and verification process.
    This record uses camelCase for orderBy, even though that is
    non-idiomatic for Clojure, to highlight the parallel to the
    orderBy GET parameter, which is part of the established API."}
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
  "Execute the query against the provided collection. This function
  does not follow the same API as the rest of the functions in this
  namespace: that is, take a query + params, return a query. Instead
  we return the query results. The results are kept out of the query
  record so that they can be garbage-collected as we iterate through
  them."
  [{:keys [dataset slice] :as query}]

  (metrics/with-timing "queries.execute"
    (let [_ (log/info "Execute query" (str (into {} (dissoc query :metadata :slicedef))))
          query (if (:prepared? query) query (prepare query))]
      (cond
        (not (valid? query))
        (do
          (metrics/increment "queries.invalid.count")
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

(defn columns
  "Return list of columns to be used in results. Assumes a prepared query."
  [{:keys [select slicedef] :as query}]
  (if (or (str/blank? select)          
          (seq (:errors query)))
    (data/slice-columns slicedef)
    (map (comp name :select) (select/parse select))))

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
