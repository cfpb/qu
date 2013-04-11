(ns cfpb.qu.query.mongo
  "This namespace populates a query with a Mongo translation of that
  query. `process` is the main entry point, although the individual
  functions have been left public, mainly for testing.

  All public functions in this namespace should adhere to the
  following contract:

  * They take a cfpb.qu.query/Query.
  * They return a cfpb.qu.query/Query.
  * If any errors are found, they populate :errors on that Query and abort.
  * If not, they populate :mongo on that Query."
  (:refer-clojure :exclude [sort])
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [clojure.walk :as walk]
            [protoflex.parse :refer [parse]]
            [taoensso.timbre :as log]
            [cfpb.qu.query.where :as where]
            [cfpb.qu.query.select :as select]
            [cfpb.qu.query.parser :as parser]))

(declare valid? match project group sort validate post-process-validate)

(defn process
  "Process the original query through the various filters used to
create the Mongo representation of the query. Main entry point into
this namespace."
  [query]
  (let [query (validate query)]
    (if (valid? query)
      (-> query
          project
          group
          match
          sort
          post-process-validate)
      query)))

(defn- valid? [query]
  (= 0 (reduce + (map count (:errors query)))))

(declare validate-select validate-group validate-where
         validate-order-by validate-limit validate-offset)

(defn validate
  "Check the query for any errors."
  [query]
  (if (not (:slicedef query))
    query
    (let [slicedef (:slicedef query)
          dimensions (:dimensions slicedef)
          metrics (:metrics slicedef)
          column-set (set (concat dimensions metrics))]
      (-> query
          (assoc :errors {})
          (validate-select column-set)
          (validate-group column-set dimensions)
          validate-where
          validate-order-by
          validate-limit
          validate-offset))))

(defn match
  "Add the :match provision of the Mongo query. Assemble the match
  from both :where and :dimensions of the origin query."
  [query]
  (if (or (:where query)
          (:dimensions query))
    (let [parse #(if %
                   (where/mongo-eval (where/parse %))
                   {})
          match (-> (:where query "")
                    parse
                    (merge (:dimensions query {})))]
      (assoc-in query [:mongo :match] match))
    query))

(defn project
  "Add the :project provision of the Mongo query from the :select of
  the origin query."
  [query]
  (if-let [select (:select query)]
    (let [project (select/mongo-eval (select/parse select))]
      (assoc-in query [:mongo :project] project))
    query))

(defn- select-to-agg
  "Convert a select/aggregation map into the Mongo equivalent for the
  $group filter of the aggregation framework. Used in the group
  function. Non-public."
  [{alias :select [agg column] :aggregation}]
  (if (= agg :COUNT)
    {alias {"$sum" 1}}
    {alias
     {(str "$" (str/lower-case (name agg)))
      (str "$" (name column))}}))

(defn group
  "Add the :group provision of the Mongo query, using both the :select
and :group provisions of the original query."
  [query]
  (if-let [group (:group query)]
    (let [columns (parse parser/group-expr group)
          id (into {} (map #(vector % (str "$" (name %))) columns))
          aggregations (->> (select/parse (:select query))
                            (filter :aggregation)
                            (map select-to-agg))
          group (apply merge {:_id id} aggregations)]
      (assoc-in query [:mongo :group] group))
    query))

(defn sort
  "Add the :sort provision of the Mongo query."
  [query]
  (let [order (:orderBy query)]
    (if-not (str/blank? order)
      (let [sort (->> (str/split order #",\s*")
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
                      (apply sorted-map))]
        (assoc-in query [:mongo :sort] sort))
      query)))

(defn- match-columns [match]
  (->> match
       (walk/prewalk (fn [element]
                       (if (map? element)
                         (into [] element)
                         element)))
       flatten
       (filter keyword?)))

(defn- add-error
  [query field message]
  (update-in query [:errors field]
             (fnil #(conj % message) (vector))))

(defn- validate-field
  [query clause column-set field]
  (let [field (name field)]
    (if (not (contains? column-set field))
      (add-error query clause (str "\"" field "\" is not a valid field."))
      query)))

(defn- validate-select-fields
  [query column-set select]
  (let [fields (map (comp name #(if (:aggregation %)
                                  (second (:aggregation %))
                                  (:select %))) select)]
    (reduce #(validate-field %1 :select column-set %2) query fields)))

(defn- validate-select-no-aggregations-without-group
  [query select]
  (if (:group query)
    query
    (if (some :aggregation select)
      (add-error query :select
                 (str "You cannot use aggregation operators "
                      "without specifying a group clause."))
      query)))

(defn- validate-select-no-non-aggregated-fields
  [query select]
  (if (:group query)
    (let [group-fields (set (parse parser/group-expr (:group query)))
          non-aggregated-fields (set (map :select (remove :aggregation select)))
          invalid-fields (set/difference non-aggregated-fields group-fields)]
      (reduce #(add-error %1 :select
                          (str "\"" (name %2)
                               "\" must either be aggregated or be in the group clause."))
              query invalid-fields))
    query))

(defn- validate-select
  [query column-set]
  (try
    (let [select (select/parse (:select query ""))]
      (-> query
          (validate-select-fields column-set select)
          (validate-select-no-aggregations-without-group select)
          (validate-select-no-non-aggregated-fields select)))
    (catch Exception e
      (add-error query :select "Could not parse this clause."))))

(defn- validate-group-requires-select
  [query]
  (if (:select query)
    query
    (add-error query :group "You must have a select clause to use grouping.")))

(defn- validate-group-fields
  [query group column-set]
  (reduce #(validate-field %1 :group column-set %2) query group))

(defn- validate-group-only-group-dimensions
  [query group dimensions]
  (let [dimensions (set dimensions)]
    (reduce
     (fn [query field]
       (let [field (name field)]
         (if (not (contains? dimensions field))
           (add-error query :group (str "\"" field "\" is not a dimension."))
           query)))
     query group)))

(defn- validate-group
  [query column-set dimensions]
  (try
    (let [group (parse parser/group-expr (:group query ""))]
      (-> query
          validate-group-requires-select
          (validate-group-fields group column-set)
          (validate-group-only-group-dimensions group dimensions)))
    (catch Exception e
      (add-error query :group "Could not parse this clause."))))

(defn- validate-where
  [query]
  (try
    (let [_ (where/parse (:where query ""))]
      query)
    (catch Exception e
      (add-error query :where "Could not parse this clause."))))

(defn- validate-order-by
  [query]
  (try
    (let [_ (parse parser/order-expr (:orderBy query ""))]
      query)
    (catch Exception e
      (add-error query :orderBy "Could not parse this clause."))))

(defn- validate-integer
  [query clause]
  (let [val (clause query)]
    (if (integer? val)
      query
      (try
        (let [_ (Integer/parseInt val)]
          query)
        (catch NumberFormatException e
          (add-error query clause "Please use an integer."))))))

(defn- validate-limit
  [query]
  (validate-integer query :limit))

(defn- validate-offset
  [query]
  (validate-integer query :offset))

(defn- post-process-validate
  [query]
  query)
