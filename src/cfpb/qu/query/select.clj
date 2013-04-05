(ns cfpb.qu.query.select
  "This namespace parses SELECT clauses into an AST."
  (:require
   [clojure.string :as str]
   [protoflex.parse :as p]
   [cfpb.qu.query.parser :refer [select-expr]]))

(defn parse [select]
  (p/parse select-expr select))


