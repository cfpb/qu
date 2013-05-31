(ns cfpb.qu.query.validation
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [protoflex.parse :refer [parse]]
            [taoensso.timbre :as log]
            [cfpb.qu.util :refer [->int first-or-identity]]
            [cfpb.qu.data :as data :refer [slice-columns]]
            [cfpb.qu.query.where :as where]
            [cfpb.qu.query.select :as select]
            [cfpb.qu.query.parser :as parser]))

(defn valid? [query]
  (or (not (:errors query))
      (zero? (reduce + (map count (:errors query))))))

(defn valid-field? [{:keys [metadata slice]} field]
  (let [identifier-regex-str (.pattern parser/identifier-regex)
        concept-regex (re-pattern (str "__(" identifier-regex-str ")\\.(" identifier-regex-str ")"))
        [concept property] (if-let [match (re-matches concept-regex field)]
                                     [(nth match 1) (nth match 2)]
                                     [field nil])
        columns (->> (slice-columns (get-in metadata [:slices (keyword slice)]))
                     (map name)
                     set)
        concept-properties (->> (get-in metadata [:concepts (keyword concept) :properties])
                                keys
                                (map name)
                                set)
        concept-in-columns (contains? columns concept)
        property-nil (nil? property)
        property-in-properties (contains? concept-properties property)]
    (and concept-in-columns
         (or property-nil property-in-properties))))

(defn- add-error
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
  (let [fields (map (comp name #(cond
                                 (:aggregation %) (second (:aggregation %))
                                 :default (:select %))) select)]
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
    (let [convert-group (fn [group]
                          (if (coll? group)
                            (str (name (first group)) "." (name (second group)))
                            (name group)))
          group-fields (set (map convert-group (parse parser/group-expr (:group query))))
          non-aggregated-fields (set (map :alias (remove :aggregation select)))
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

(defn- convert-group-to-mongo-form
  [group]
  (if (coll? group)
    (str "__" (str/join "." (map name group)))
    group))

(defn- validate-group-fields
  [query group]
  (let [group (map convert-group-to-mongo-form group)]
    (reduce #(validate-field %1 :group %2) query group)))

(defn- validate-group-only-group-dimensions
  [{:keys [slicedef] :as query} group]
  (let [dimensions (set (:dimensions slicedef))]
    (reduce
     (fn [query field]
       (let [field (name field)]
         (if (contains? dimensions field)
           query
           (add-error query :group (str "\"" field "\" is not a dimension.")))))
     query (map first-or-identity group))))

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
        (log/error "Exception:" (class e) e)
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
  (-> query
      (validate-integer :limit)
      (validate-max-limit 1000)))

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
