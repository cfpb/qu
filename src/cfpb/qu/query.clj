(ns cfpb.qu.query
  (:require [clojure.string :as str]
            [monger.query :as q]
            [cfpb.qu.query.mongo :as mongo]
            [lonocloud.synthread :as ->]))

(def default-limit 100)
(def default-offset 0)

(declare parse-params)

(defrecord Query [select group where order limit offset mongo errors])

(defn params->Query
  "Convert params from a web request plus a slice definition into a
  Query record."
  [params slice]
  (let [{:keys [dimensions clauses]} (parse-params params slice)
        select (:$select clauses)
        group (:$group clauses)
        order (:$orderBy clauses)
        where (:$where clauses)
        limit (Integer/parseInt (:$limit clauses
                                         (str default-limit)))
        offset (Integer/parseInt (:$offset clauses
                                           (str default-offset)))]
    (map->Query {:select select
                 :group group
                 :where where
                 :limit limit
                 :offset offset
                 :order order
                 :dimensions dimensions})))

(defn is-aggregation? [query]
  (:group query false))

(defn Query->mongo
  "Transform a Query record into a Mongo query map."
  [query]
  (let [query (mongo/process query)
        mongo (q/partial-query
               (q/find (get-in query [:mongo :match]))
               (q/limit (or (:limit query)
                            default-limit))
               (q/skip (or (:offset query)
                           default-offset))
               (q/sort (get-in query [:mongo :sort])))]
    (if-let [project (get-in query [:mongo :project])]
      (merge mongo {:fields project})
      mongo)))

(defn Query->aggregation [query]
  ;; TODO
  ;; handle grouping on more than one thing
  ;; put groupings into $project
  ;; handle SUM, MIN, MAX, COUNT
  (let [query (mongo/process query)
        match (get-in query [:mongo :match])
        project (get-in query [:mongo :project])
        group (get-in query [:mongo :group])
        sort (get-in query [:mongo :sort])        
        skip (:offset query)
        limit (:limit query)]
    (-> []
        (->/when match
          (conj {"$match" match}))
        (->/when group
          (conj {"$group" group}))        
        (conj {"$project" project})
        (->/when sort
          (conj {"$sort" sort}))        
        (->/when skip
          (conj {"$skip" skip}))
        (->/when limit
          (conj {"$limit" limit})))))

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
