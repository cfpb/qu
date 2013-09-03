(ns cfpb.qu.query.validation
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [protoflex.parse :refer [parse]]
            [taoensso.timbre :as log]
            [cfpb.qu.util :refer [->int first-or-identity ->print]]
            [cfpb.qu.data :as data :refer [slice-columns]]
            [cfpb.qu.query.where :as where]
            [cfpb.qu.query.select :as select]
            [cfpb.qu.query.parser :as parser]))

(defn valid? [query]
  (or (not (:errors query))
      (zero? (reduce + (map count (:errors query))))))

(defn valid-field? [{:keys [metadata slice]} field]
  (let [fields (->> (slice-columns (get-in metadata [:slices (keyword slice)]))
                     (map name)                     
                     set)
        fields (conj fields "_id")]
    (contains? fields field)))

(defn add-error
  [query field message]
  (update-in query [:errors field]
             (fnil #(conj % message) (vector))))

(defn validate-field
  [query clause field]
  (let [field (name field)]
    (if (valid-field? query field)
      query
      (add-error query clause (str "\"" field "\" is not a valid field.")))))


(defn- validate-select-fields
  [query select]
  (let [fields (map (comp name #(if (:aggregation %)
                                  (second (:aggregation %))
                                  (:select %)))
                    select)]
    (reduce #(validate-field %1 :select %2) query fields)))

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
    (let [group-fields (set (map name (parse parser/group-expr (:group query))))
          non-aggregated-fields (set (map (comp name :select) (remove :aggregation select)))
          invalid-fields (set/difference non-aggregated-fields group-fields)]
      (reduce #(add-error %1 :select
                          (str "\"" (name %2)
                               "\" must either be aggregated or be in the group clause."))
              query invalid-fields))
    query))

(defn- validate-select
  [query]
  (try
    (let [select (select/parse (str (:select query)))]
      (-> query
          (validate-select-fields select)
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
  [query group]
  (reduce #(validate-field %1 :group %2) query group))

(defn- validate-group-only-group-dimensions
  [{:keys [slicedef] :as query} group]
  (let [dimensions (set (:dimensions slicedef))]
    (reduce
     (fn [query field]
       (let [field (name field)]
         (if (contains? dimensions field)
           query
           (add-error query :group (str "\"" field "\" is not a dimension.")))))
     query group)))

(defn- validate-group
  [query]
  (if-let [group (:group query)]
    (try
      (let [group (parse parser/group-expr group)]
        (-> query
            validate-group-requires-select
            (validate-group-fields group)
            (validate-group-only-group-dimensions group)))
      (catch Exception e
        (add-error query :group "Could not parse this clause.")))
    query))

(defn- validate-where
  [query]
  (try
    (let [_ (where/parse (str (:where query)))]
      query)
    (catch Exception e
      (add-error query :where "Could not parse this clause."))))

(defn- validate-order-by
  [query]
  (try
    (let [_ (parse parser/order-by-expr (str (:orderBy query)))]
      query)
    (catch Exception e
      (add-error query :orderBy "Could not parse this clause."))))

(defn- validate-integer
  [query clause]
  (let [val (clause query)]
    (if (integer? val)
      query
      (try
        (let [_ (cond
                 (integer? val) val
                 (nil? val) 0
                 :default (Integer/parseInt val))]
          query)
        (catch NumberFormatException e
          (add-error query clause "Please use an integer."))))))

(defn- validate-max-limit
  [query max]
  (let [limit (->int (:limit query) 0)]
    (if (> limit max)
      (add-error query :limit (str "The maximum limit is " max "."))
      query)))

(defn- validate-limit
  [query]
  (validate-integer query :limit))

(defn- validate-offset
  [query]
  (validate-integer query :offset))

(defn validate
  "Check the query for any errors."
  [query]
  (if-let [metadata (:metadata query)]
    (let [slice (:slice query)
          slicedef (get-in metadata [:slices (:slice query)])
          dimensions (:dimensions slicedef)]
      (-> query
          (assoc :errors {})
          validate-select
          validate-group
          validate-where
          validate-order-by
          validate-limit
          validate-offset))
    query))
