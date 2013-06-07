(ns cfpb.qu.query.concepts
  (:require [clojure.string :as str]
            [cfpb.qu.query.parser :as parser]))

(def prefix "__")

(defn field-name
  [concept field]
  (str (name concept) "." (name field)))

(defn db-name
  [concept field]
  (str prefix (field-name concept field)))

(defn regex
  ([] (regex true))
  ([dashes-optional]
     (let [identifier-regex-str (.pattern parser/identifier-regex)]
       (re-pattern (str (if dashes-optional (str "(?:" prefix ")?") prefix)
                        "("
                        identifier-regex-str
                        ")\\.("
                        identifier-regex-str
                        ")")))))

(defn split
  [concept-field]
  (let [match (re-matches (regex) concept-field)]
    (if (seq match)
      (map keyword (vector (nth match 1) (nth match 2))))))
