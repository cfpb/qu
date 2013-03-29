(ns cfpb.qu.query
  (:require [clojure.string :as str]
            [monger.query :as q]
            [cfpb.qu.where :as where]))

(defrecord Query [select where order limit offset])

(def default-limit 100)
(def default-offset 0)

(defn make-query [& rest]
  (let [args (apply hash-map rest)]
    (->Query (:select args)
             (:where args)
             (:order args)
             (:limit args default-limit)
             (:offset args default-offset))))

;; TODO move this to a better location
(defn select-fields
  "In API requests, the user can select the columns they want
  returned. If they choose to do this, the columns will be in a
  comma-separated string. This function returns a seq of column names
  from that string."
  [select]
  (if select
    (str/split select #",\s*")))

(defn- order-by-sort
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

(defn- where->mongo [where]
  (if where
    (where/mongo-eval (where/parse where))
    {}))

(defn make-query-from-params [{:keys [dimensions clauses]}]
  (let [select (select-fields (:$select clauses))
        limit (Integer/parseInt (:$limit clauses
                                         (str default-limit)))
        offset (Integer/parseInt (:$offset clauses
                                           (str default-offset)))
        order (or (order-by-sort (:$orderBy clauses))
                  {})
        where (:$where clauses)]
    (map->Query {:select select
                 :where where
                 :limit limit
                 :offset offset
                 :order order
                 :dimensions dimensions})))

(defn query->mongo [query]
  (let [where (->> (:where query)
                   where->mongo
                   (merge (:dimensions query {})))
        mongo (q/partial-query
               (q/find where)
               (q/limit (:limit query))
               (q/skip (:offset query))
               (q/sort (:order query)))]
    (if-let [select (:select query)]
      (merge mongo {:fields select})
      mongo)))
