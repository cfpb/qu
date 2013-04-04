(ns cfpb.qu.query
  (:require [clojure.string :as str]
            [monger.query :as q]
            [cfpb.qu.query.where :as where]
            [cfpb.qu.query.select :as select]
            [lonocloud.synthread :as ->]
            [clojure.walk :as walk]))

(def default-limit 100)
(def default-offset 0)

(declare select-fields parse-params)

(defrecord Query [select group where order limit offset])

(defn params->Query
  "Convert params from a web request plus a slice definition into a
  Query record."
  [params slice]
  (let [{:keys [dimensions clauses]} (parse-params params slice)
        select (:$select clauses)
        group (:$group clauses)
        order (or (order-by-sort (:$orderBy clauses))
                  {})
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
  (or
   (:group query false)
   (re-find #"(?i)\bAS\b" (:select query ""))))

(defn- where->mongo [where]
  (if where
    (where/mongo-eval (where/parse where))
    {}))

(defn order->mongo
  "In API requests, the user can select the order of data returned. If
  they do this, the order will be specified as a comma-separated
  string like so: 'column [desc], column, ...'. This function takes
  that string and returns a sorted map consisting of columns as keys
  and -1/1 for values depending on whether the sort is descending or
  ascending for that column."
  [order-by]
  (if order-by
    (->> (str/split order-by #",\s*")
         (map (fn [order]
                (let [order (str/split order #"\s+")]
                  ;; TODO refactor
                  (if (= (count order) 2)
                    (vector (first order)
                            (if (= "desc" (str/lower-case (second order)))
                              -1
                              1))
                    (vector (first order) 1)))))
         flatten
         (apply sorted-map))))

(defn- Query->mongo-where [query]
  (-> (:where query)
      (where->mongo)
      (merge (:dimensions query {}))))

(defn- match-columns [match]
  (->> match
       (walk/prewalk (fn [element]
                       (if (map? element)
                         (into [] element)
                         element)))
       flatten
       (filter keyword?)))

(defn Query->mongo
  "Transform a Query record into a Mongo query map."
  [query]
  (let [where (Query->mongo-where query)
        fields (if-let [select (:select query)]
                 (select/parse select))
        order (order->mongo (:order query ""))
        mongo (q/partial-query
               (q/find where)
               (q/limit (:limit query))
               (q/skip (:offset query))
               (q/sort order))]
    (if fields
      (merge mongo {:fields fields})
      mongo)))

(defn Query->aggregation [query]
  ;; TODO
  ;; handle grouping on more than one thing
  ;; put groupings into $project
  ;; handle SUM, MIN, MAX, COUNT
  (let [match (Query->mongo-where query)
        select (:select query)
        select (if select (select/parse select))
        projection (merge
                    (or select {})
                    (into {} (map #(vector % 1) (match-columns match))))
        _ (println projection)
        group (:group query)
        skip (:offset query)
        limit (:limit query)]
    (-> []
        (->/when select
          (conj {"$project" projection}))
        (conj {"$match" match})
        (->/when group
          (conj {"$group" {"_id" (str "$" (:group query))}}))
        (->/when skip
          (conj {"$skip" skip}))
        (->/when limit
          (conj {"$limit" limit})))))

;; TODO move this to a better location
(defn select-fields
  "In API requests, the user can select the columns they want
  returned. If they choose to do this, the columns will be in a
  comma-separated string. This function returns a seq of column names
  from that string."
  [select]
  (if select
    (str/split select #",\s*")))

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
