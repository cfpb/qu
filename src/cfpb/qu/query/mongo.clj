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

(declare match project group sort validate)

(defn process
  "Process the original query through the various filters used to
create the Mongo representation of the query. Main entry point into
this namespace."
  [query]
  (-> query
      match
      project
      group
      sort
      validate))

(defn match
  "Add the :match provision of the Mongo query. Assemble the match
  from both :where and :dimensions of the origin query."
  [query]
  (if (or (:where query)
          (:dimensions query))
    (try
      (let [parse #(if %
                     (where/mongo-eval (where/parse %))
                     {})
            match (-> (:where query)
                      parse
                      (merge (:dimensions query {})))]
        (assoc-in query [:mongo :match] match))
      (catch Exception e
        (assoc-in query [:errors :where] "could not parse")))
    query))

(defn project
  "Add the :project provision of the Mongo query from the :select of
  the origin query."
  [query]
  (if-let [select (:select query)]
    (try
      (let [project (select/mongo-eval (select/parse select))]
        (assoc-in query [:mongo :project] project))
      (catch Exception e
        (assoc-in query [:errors :select] "could not parse")))
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
    (try
      (let [columns (parse parser/group-expr group)
            id (into {} (map #(vector % (str "$" (name %))) columns))
            aggregations (->> (select/parse (:select query))
                              (filter :aggregation)
                              (map select-to-agg))
            group (apply merge {:_id id} aggregations)]
        (assoc-in query [:mongo :group] group))
      (catch Exception e
        (assoc-in query [:errors :group] "could not parse")))
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
  [query column-set field]
  (if (not (contains? column-set field))
    (add-error query :select (str "\"" field "\" is not a valid field."))
    query))

(defn- validate-fields
  [query column-set select]
  (let [fields (map (comp name #(if (:aggregation %)
                                  (second (:aggregation %))
                                  (:select %))) select)]
    (reduce #(validate-field %1 column-set %2) query fields)))

(defn- validate-no-aggregations-without-group
  [query select]
  (if (:group query)
    query
    (if (some :aggregation select)
      (add-error query :select
                 (str "You cannot use aggregation operators "
                      "without specifying a group clause."))
      query)))

(defn- validate-no-non-aggregated-fields
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
  (let [select (select/parse (:select query))]
    (-> query
        (validate-fields column-set select)
        (validate-no-aggregations-without-group select)
        (validate-no-non-aggregated-fields select))))

(defn validate
  "Check the query for any errors."
  [query]
  (if (:errors query)
    query
    (if-let [slicedef (:slicedef query)]
      (let [dimensions (:dimensions slicedef)
            metrics (:metrics slicedef)
            column-set (set (concat dimensions metrics))]
        (-> query
            (validate-select column-set)))
      query)))
