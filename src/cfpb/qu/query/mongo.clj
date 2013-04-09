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
            [protoflex.parse :refer [parse]]
            [cfpb.qu.query.where :as where]
            [cfpb.qu.query.select :as select]
            [cfpb.qu.query.parser :as parser]))

(declare match project group sort)

(defn process
  "Process the original query through the various filters used to
create the Mongo representation of the query. Main entry point into
this namespace."
  [query]
  (-> query
      match
      project
      group
      sort))

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
