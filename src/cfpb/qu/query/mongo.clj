(ns cfpb.qu.query.mongo
  (:refer-clojure :exclude [sort])
  (:require [clojure.string :as str]
            [protoflex.parse :refer [parse]]
            [cfpb.qu.query.where :as where]
            [cfpb.qu.query.select :as select]
            [cfpb.qu.query.parser :as parser]))

(defn sort [query]
  (let [order (:order query)]
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

(defn match [query]
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

(defn project [query]
  (if-let [select (:select query)]
    (try
      (let [project (-> (select/parse select)
                        select/mongo-eval)]
        (assoc-in query [:mongo :project] project))
      (catch Exception e
        (assoc-in query [:errors :select] "could not parse")))
    query))

(defn group [query]
  (if-let [group (:group query)]
    ;; Ensure this works even if project hasn't been called yet.
    (let [query (if (get-in query [:mongo :project])
                  query
                  (project query))]
      (try
        (let [columns (parse parser/group-expr group)
              id (into {} (map #(vector % (str "$" (name %))) columns))
              aggregations (->> (select/parse (:select query))
                                (filter :aggregation)
                                (map
                                 (fn [{alias :select
                                       [agg column] :aggregation}]
                                   {alias
                                    {(str "$" (str/lower-case (name agg)))
                                     (str "$" (name column))}})))
              group (apply merge {:_id id} aggregations)]
          (assoc-in query [:mongo :group] group))
        (catch Exception e
          (assoc-in query [:errors :group] "could not parse"))))
    query))

(defn process [query]
  (-> query
      match
      project
      group
      sort))
