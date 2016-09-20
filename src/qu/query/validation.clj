(ns qu.query.validation
  (:require [clojure.set :as set]
            [protoflex.parse :refer [parse]]
            [qu.data :refer [slice-columns]]
            [qu.query.parser :as parser]
            [qu.query.select :as select]
            [qu.query.where :as where]
            [qu.util :refer [->int]]))

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
    (reduce (fn [query field] (validate-field query :select field)) query fields)))

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

(defn- validate-group-fields-max-count
  [{:keys [slicedef] :as query} group]
  (let [max-field-count (:max-group-fields slicedef 5)]
    (if (> (count group) max-field-count)
      (add-error query :group (str "Number of group fields exceeds maximum allowed (" max-field-count ")."))
      query)))

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
            (validate-group-fields-max-count group)
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

(defn- validate-positive-integer
  [query clause]
  (let [query (validate-integer query clause)
        val (->int (clause query) 1)]
    (if (>= 0 val)
      (add-error query clause "Should be positive")
      query)))


(defn- validate-max-offset
  [query]
  (let [offset (->int (:offset query) 0)]
    (if (> offset 10000)
      (add-error query :offset (str "The maximum offset is 10,000."))
      query)))

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

(defn- validate-page
  [query]
  (validate-positive-integer query :page))


(defn validate
  "Check the query for any errors."
  [query]
  (if-let [metadata (:metadata query)]
    (-> query
        (assoc :errors {})
        validate-select
        validate-group
        validate-where
        validate-order-by
        validate-limit
        validate-offset
        validate-page
        validate-max-offset)
    query))
