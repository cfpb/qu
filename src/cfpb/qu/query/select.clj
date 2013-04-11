(ns cfpb.qu.query.select
  "This namespace parses SELECT clauses into an AST."
  (:require
   [clojure.string :as str]
   [protoflex.parse :as p]
   [cfpb.qu.query.parser :refer [select-expr]]))

(defn parse [select]
  (p/parse select-expr select))

(declare mongo-eval-aggregation)

(defn- is-aggregation? [ast]
  (some :aggregation ast))

(defn mongo-eval [ast]
  (if (is-aggregation? ast)
    (mongo-eval-aggregation ast)
    (->> ast
         (map :select)
         (map #(hash-map % 1))
         (apply merge))))

(defn- convert-select [column]
  (let [column-name (name (:select column))]
    (if (:aggregation column)
      {column-name (str "$" column-name)}
      {column-name (str "$_id." column-name)})))

(defn- mongo-eval-aggregation [ast]
  (->> ast
       vec
       (map convert-select)
       (apply merge)))
