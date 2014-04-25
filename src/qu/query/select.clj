(ns qu.query.select
  "This namespace parses SELECT clauses into an AST."
  (:require [clojure.string :as str]
            [protoflex.parse :as p]
            [qu.query.parser :refer [select-expr]]))

(defn parse [select]
  (p/parse select-expr select))

(defn- is-aggregation? [ast]
  (some :aggregation ast))

(defn mongo-eval-aggregation [ast]
  (let [ast (vec ast)
        aggregations (->> (filter :aggregation ast)
                          (map (fn [{:keys [select aggregation]}]
                                 (let [aggregation (map (comp str/lower-case name) aggregation)]
                                   (if (= (first aggregation) "count")
                                     {select ["count" 1]}
                                     {select aggregation}))))
                          (apply merge))
        fields (map :select ast)]
    {:fields fields
     :aggregations aggregations}))

(defn mongo-eval [ast & {:keys [aggregation]}]
  (if (or aggregation (is-aggregation? ast))
    (mongo-eval-aggregation ast)
    (->> ast
         (map :select)
         (map #(hash-map % 1))
         (apply merge))))

