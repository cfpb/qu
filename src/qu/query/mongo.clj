(ns qu.query.mongo
  "This namespace populates a query with a Mongo translation of that
  query. `process` is the main entry point, although the individual
  functions have been left public, mainly for testing.

  All public functions in this namespace should adhere to the
  following contract:

  * They take a qu.query/Query.
  * They return a qu.query/Query.
  * If any errors are found, they populate :errors on that Query and abort.
  * If not, they populate :mongo on that Query."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [protoflex.parse :refer [parse]]
            [qu.query.parser :as parser]
            [qu.query.select :as select]
            [qu.query.validation :refer [add-error valid?
                                         validate-field]]
            [qu.query.where :as where])
  (:refer-clojure :exclude [sort]))

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
  from the :where of the origin query."
  [query]
  (if (:where query)
    (let [parse #(where/mongo-eval (where/parse %))
          match (parse (str (:where query)))]
      (assoc-in query [:mongo :match] match))
    query))

(defn project
  "Add the :project provision of the Mongo query from the :select of
  the origin query."
  [query]
  (if-let [select (str (:select query))]
    (let [project (select/mongo-eval (select/parse select)
                                     :aggregation (:group query false))]
      (assoc-in query [:mongo :project] project))
    query))

(defn group
  "Add the :group provision of the Mongo query, using both the :select
and :group provisions of the original query."
  [query]
  (if-let [group (str (:group query))]
    (let [columns (parse parser/group-expr group)]
      (assoc-in query [:mongo :group] columns))
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
                      flatten
                      (apply array-map))]
        (assoc-in query [:mongo :sort] sort))
      query)))

(defn- match-fields [match]
  (->> match
       (walk/prewalk (fn [element]
                       (if (map? element)
                         (vec element)
                         element)))
       flatten
       (filter #(and (keyword? %) (not (= \$ (first (name %))))))))

(defn- validate-match-fields [query metadata slice]
  (let [fields (match-fields (get-in query [:mongo :match]))]
    (reduce #(validate-field %1 :where %2) query fields)))

(defn- validate-order-fields-aggregation [query order-fields]
  (let [available-fields (set (get-in query [:mongo :project :fields]))
        order-fields (set (map keyword order-fields))
        invalid-fields (set/difference order-fields available-fields)]
    (reduce #(add-error %1 :orderBy
                        (str "\"" (name %2)
                             "\" is not an available field for sorting."))
            query invalid-fields)))

(defn- validate-order-fields [query metadata slice]
  (let [order-fields (keys (get-in query [:mongo :sort]))
        group (get-in query [:mongo :group])]
    (if (str/blank? (:group query))
      (reduce #(validate-field %1 :orderBy %2) query order-fields)
      (validate-order-fields-aggregation query order-fields))))

(defn post-validate [query]
  (let [metadata (:metadata query)
        slice (:slice query)]
    (-> query
        (validate-match-fields metadata slice)
        (validate-order-fields metadata slice))))

