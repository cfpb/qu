(ns qu.query.parser
  "Parse functions for queries."
  (:require [clj-time.core :as time]
            [clojure.string :as str]
            [protoflex.parse :refer [any attempt chr chr-in dq-str
                                     multi* number parens regex sep-by*
                                     series sq-str starts-with?
                                     string-in]]))

(def identifier-regex #"[A-Za-z][A-Za-z0-9\-_]*")

(defn- ci-string
  "Match case insensitive strings."
  [string]
  (regex (re-pattern (str "(?i)\\Q" string "\\E"))))

(defn- string-literal []
  (any dq-str sq-str))

(defn- date-literal []
  (let [year (regex #"\d{4}")
        _ (chr-in [\- \/])
        month (regex #"\d{1,2}")
        _ (chr-in [\- \/])
        day (regex #"\d{1,2}")
        ymd (map #(Integer/parseInt %) [year month day])]
    (apply time/date-time ymd)))

(defn- boolean-literal
  "Match the boolean literals true and false. Case-insensitive. We
have to return a map instead of the boolean value because returning
false will make the parser think it's failed to match."
  []
  (let [lit (any #(ci-string "true")
                 #(ci-string "false"))]
    {:bool (= (str/lower-case lit) "true")}))

(defn value
  "Parse expression for values in WHERE queries. Valid values are numbers,
numeric expressions, strings, and booleans."
  []
  (any date-literal number string-literal boolean-literal))

(defn list-of-values
  []
  (let [_ (chr \()
        values (sep-by* value #(chr \,) #(chr \)))]
    values))

(defn- comparison-operator []
  (let [op (string-in [">" ">=" "=" "!=" "<" "<=" "LIKE" "ILIKE"])]
    (keyword op)))

(defn identifier
  "Parse function for identifiers in WHERE queries. Valid identifiers
begin with a letter and are made up of letters, numbers, dashes, and
underscores."
  []
  (let [ident (regex identifier-regex)]
    (keyword ident)))

(defn- comparison-normal []
  (let [[identifier op value]
        (series identifier comparison-operator value)]
    {:comparison [identifier op value]}))

(defn- comparison-null []
  (let [identifier (identifier)
        is-null (ci-string "IS NULL")]
    {:comparison [identifier := nil]}))

(defn- comparison-not-null []
  (let [identifier (identifier)
        is-null (ci-string "IS NOT NULL")]
    {:comparison [identifier :!= nil]}))

(defn- comparison-in []
  (let [identifier (identifier)
        _ (ci-string "IN")
        values (list-of-values)]
    {:comparison [identifier :IN values]}))

(defn comparison
  "Parse function for comparisons in WHERE queries. Comparisons are
made up of an identifier and then either a comparison operator and a
value or the phrases 'IS NULL' or 'IS NOT NULL'."
  []
  (any comparison-normal
       comparison-in
       comparison-null
       comparison-not-null))

(defn- and-or-operator []
  (let [op (any #(ci-string "AND")
                #(ci-string "OR"))]
    (keyword (str/upper-case op))))

(defn- not-operator []
  (let [op (ci-string "NOT")]
    (keyword (str/upper-case op))))

(declare where-expr)

(defn- paren-where-expr []
  (chr \()
  (let [expr (where-expr)]
    (chr \))
    expr))

(defn- boolean-factor []
  (let [not-operator (attempt not-operator)
        factor (if (starts-with? "(")
                 (paren-where-expr)
                 (comparison))]
    (if not-operator
      {:not factor}
      factor)))

(defn- build-boolean-tree
  "Take a vector of boolean factors separated by boolean operators and
turn it into a tree built in proper precedence order."
  [nodes]
  (let [nc (count nodes)]
    (assert (and
             (>= nc 3)
             (odd? nc)))
    (if (= nc 3)
      {:left (nth nodes 0) :op (nth nodes 1) :right (nth nodes 2)}
      {:left (build-boolean-tree (take (- nc 2) nodes))
       :op (nth nodes (- nc 2))
       :right (nth nodes (- nc 1))})))

(defn where-expr
  "The parse function for valid WHERE expressions."
  []
  (if-let [left (attempt boolean-factor)]
    (if-let [rhs (multi* #(series and-or-operator boolean-factor))]
      (build-boolean-tree
       (into [left] (apply concat rhs)))
      left)))

(defn- comma []
  (chr \,))

(defn- simple-select
  []
  (let [column (identifier)]
    {:select column}))

(defn- aggregation []
  (let [agg (any #(ci-string "SUM")
                 #(ci-string "COUNT")
                 #(ci-string "MAX")
                 #(ci-string "MIN")
                 #(ci-string "AVG"))]
    (keyword (str/upper-case agg))))

(defn- count-select []
  (let [_ (ci-string "COUNT()")]
    {:aggregation [:COUNT :_id]
     :select :count}))

(defn- aggregation-select
  []
  (let [aggregation (aggregation)
        column (parens identifier)
        alias (keyword (str (str/lower-case (name aggregation))
                            "_"
                            (str/join "_" (map name (flatten (vector column))))))]
    {:aggregation [aggregation column]
     :select alias}))

(defn- select
  []
  (any aggregation-select
       count-select
       simple-select))

(defn select-expr
  "The parse function for valid SELECT expressions.

   - state
   - state, county
   - state, SUM(population)
   - state, SUM(population)"
  []
  (if-let [fst (attempt select)]
    (if-let [rst (multi* #(series comma select))]
      (concat (vector fst) (map second rst))
      (vector fst))))

(defn group-expr
  "The parse function for valid GROUP expressions."
  []
  (if-let [fst (attempt identifier)]
    (if-let [rst (multi* #(series comma identifier))]
      (concat (vector fst) (map second rst))
      (vector fst))))

(defn- order-by
  []
  (let [mod-expr #(regex #"(?i)ASC|DESC")
        column (identifier)
        modifier (attempt mod-expr)]
    [column (keyword (str/upper-case (or modifier "ASC")))]))

(defn order-by-expr
  "The parse function for valid ORDER BY expressions.

  - state,
  - state, county
  - state ASC
  - state DESC
  - state DESC, county

  ASC is the default."
  []
  (if-let [fst (attempt order-by)]
    (if-let [rst (multi* #(series comma order-by))]
      (concat (vector fst) (map second rst))
      (vector fst))))
