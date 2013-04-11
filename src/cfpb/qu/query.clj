(ns cfpb.qu.query
  (:require [clojure.string :as str]
            [monger.query :as q]
            [monger.collection :as coll]
            [cfpb.qu.data :as data]
            [cfpb.qu.query.mongo :as mongo]
            [lonocloud.synthread :as ->]
            [taoensso.timbre :as log]))

(def default-limit 100)
(def default-offset 0)

(declare parse-params mongo-find mongo-aggregation)

(defrecord Query [select group where orderBy limit offset mongo errors slicedef])

(defn params->Query
  "Convert params from a web request plus a slice definition into a Query record."
  [params slicedef]
  (let [{:keys [dimensions clauses]} (parse-params params slicedef)
        select (:$select clauses)
        group (:$group clauses)
        orderBy (:$orderBy clauses)
        where (:$where clauses)
        limit (:$limit clauses)
        offset (:$offset clauses)]
    (map->Query {:select select
                 :group group
                 :where where
                 :limit limit
                 :offset offset
                 :orderBy orderBy
                 :dimensions dimensions
                 :slicedef slicedef})))

(defn is-aggregation? [query]
  (:group query false))

(defn execute
  "Execute the query against the provided collection."
  [collection query]
  (let [_ (log/info (str "Raw query: " (into {} query)))
        query (mongo/process query)
        _ (log/info (str "Mongo parts: " (:mongo query)))
        _ (log/info (str "Query errors: " (:errors query)))]
    (assoc query :result
           (cond
            (:errors query) []

            (is-aggregation? query)
            (data/get-aggregation collection (mongo-aggregation query))

            :default
            (data/get-find collection (mongo-find query))))))

(def allowed-clauses #{:$select :$where :$orderBy :$group :$limit :$offset})

(defn- cast-value [value type]
  (case type
    "integer" (Integer/parseInt value)
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

(defn- ->int [val]
  (cond
   (nil? val) 0
   (integer? val) val
   :default (Integer/parseInt val)))

(defn mongo-find
  "Create a Mongo find map from the query."
  [query]
  (let [mongo (q/partial-query
               (q/find (get-in query [:mongo :match]))
               (q/limit (or (->int (:limit query))
                            default-limit))
               (q/skip (or (->int (:offset query))
                           default-offset))
               (q/sort (get-in query [:mongo :sort])))]
    (if-let [project (get-in query [:mongo :project])]
      (merge mongo {:fields project})
      mongo)))

(defn mongo-aggregation
  "Add a Mongo aggregation map to the query."
  [query]
  (let [match (get-in query [:mongo :match])
        project (get-in query [:mongo :project])
        group (get-in query [:mongo :group])
        sort (get-in query [:mongo :sort])
        skip (->int (:offset query))
        limit (->int (:limit query))]
    (-> []
        (->/when match
          (conj {"$match" match}))
        (->/when group
          (conj {"$group" group}))
        (->/when project
          (conj {"$project" project}))
        (->/when sort
          (conj {"$sort" sort}))
        (->/when (not= skip 0)
          (conj {"$skip" skip}))
        (->/when (not= limit 0)
          (conj {"$limit" limit})))))

