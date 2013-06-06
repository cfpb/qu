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
            [clojure.walk :as walk]
            [protoflex.parse :refer [parse]]
            [taoensso.timbre :as log]
            [lonocloud.synthread :as ->]
            [cfpb.qu.query.where :as where]
            [cfpb.qu.query.select :as select]
            [cfpb.qu.query.parser :as parser]
            [cfpb.qu.query.concepts :as concepts]            
            [cfpb.qu.query.validation :refer [valid? validate-field]]))

(declare match project group sort post-validate)

(defn process
  "Process the original query through the various filters used to
create the Mongo representation of the query. Main entry point into
this namespace."
  [query]
  (if (valid? query)
    (-> query
        match
        project
        group
        sort
        post-validate)
    query))

(defn match
  "Add the :match provision of the Mongo query. Assemble the match
  from both :where and :dimensions of the origin query."
  [query]
  (if (or (:where query)
          (:dimensions query))
    (let [parse #(if %
                   (where/mongo-eval (where/parse %))
                   {})
          match (-> (str (:where query))
                    parse
                    (merge (:dimensions query {})))]
      (assoc-in query [:mongo :match] match))
    query))

(defn project
  "Add the :project provision of the Mongo query from the :select of
  the origin query."
  [query]
  (if-let [select (str (:select query))]
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
  (if-let [group (str (:group query))]
    (let [columns (parse parser/group-expr group)
          columns (map (comp keyword #(if (coll? %)
                                       (str concepts/prefix (name (first %)))
                                       %)) columns)
          id (into {} (map #(vector % (str "$" (name %))) columns))
          aggregations (->> (select/parse (str (:select query)))
                            (filter :aggregation)
                            (map select-to-agg))
          group (apply merge {:_id id} aggregations)]
      (assoc-in query [:mongo :group] group))
    query))

(defn sort
  "Add the :sort provision of the Mongo query."
  [query]
  (let [order (str (:orderBy query))]
    (if-not (str/blank? order)
      (let [sort (->> order
                      (parse parser/order-by-expr)
                      (map (fn [[field dir]]
                             (if (= dir :ASC)
                               [field 1]
                               [field -1])))
                      (map (fn [[alias dir]]
                             (if-let [field (get-in query [:aliases (keyword alias)])]
                               [field dir]
                               [alias dir])))
                      flatten
                      (apply sorted-map))]
        (assoc-in query [:mongo :sort] sort))
      query)))


(defn- match-fields [match]
  (->> match
       (walk/prewalk (fn [element]
                       (if (map? element)
                         (vec element)
                         element)))
       flatten
       (filter keyword?)))

(defn- validate-match-fields [query metadata slice]
  (let [fields (match-fields (get-in query [:mongo :match]))]
    (reduce #(validate-field %1 :where %2) query fields)))

(defn- validate-order-fields [query metadata slice]
  (let [order-fields (map (fn [field]
                            (if-let [match (re-find #"^__(.*?)\." (name field))]
                              (keyword (second match))
                              field)) (keys (get-in query [:mongo :sort])))
        group (get-in query [:mongo :group])]
    (if (str/blank? (:group query))
      (reduce #(validate-field %1 :orderBy %2) query order-fields)
      query)))

(defn post-validate [query]
  (let [metadata (:metadata query)
        slice (:slice query)]
    (-> query
        (validate-match-fields metadata slice)
        (validate-order-fields metadata slice))))

