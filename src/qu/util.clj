(ns qu.util
  "Utility functions for use throughout Qu."
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.walk :refer [postwalk]]
            [ring.util.response :refer [content-type]]))

(defn json-response
  "Wraps the response in the json content type and generates JSON from the content"
  [content]
  (content-type {:body (json/generate-string content)}
                "application/json; charset=utf-8"))

(defn request-protocol
  [request]
  (if-let [proto (get-in request [:headers "x-forwarded-proto"])]
    proto
    (name (:scheme request))))

(defn base-url
  [req]
  (str (request-protocol req) "://" (get-in req [:headers "host"])))

(defn str+
  [& args]
  (str/join (map name args)))

(defn apply-kw
  "Like apply, but f takes keyword arguments and the last argument is
  not a seq but a map with the arguments for f."
  [f & args]
  {:pre [(map? (last args))]}
  (apply f (apply concat
                  (butlast args) (last args))))

(defn is-int? [^Double num]
  (and (not (or (Double/isNaN num)
                (Double/isInfinite num)))
       (= num (Math/rint num))))

(defn ->int
  "Convert strings and integers to integers. A blank string or
anything but a string or integer will return the default value, which
is nil unless specified."
  ([val] (->int val nil))
  ([val default]
     (cond
      (integer? val) val
      (and (string? val) (not (str/blank? val)))
      (try (Integer/parseInt val)
           (catch NumberFormatException e default))
      :default default)))

(defn ->num
  "Convert strings and numbers to numbers. A blank string or
anything but a string or number will return the default value, which
is nil unless specified."
  ([val] (->num val nil))
  ([val default]
     (cond
      (number? val) val
      (and (string? val) (not (str/blank? val)))
      (try (Float/parseFloat val)
           (catch NumberFormatException e default))
      :default default)))

(defn ->bool
  "Convert anything to a boolean."
  [val]
  (not (not val)))

(defn first-or-identity
  "If the argument is a collection, return the first element in the
  collection, else return the argument."
  [thing]
  (if (coll? thing)
    (first thing)
    thing))

(defn convert-keys
  "Recursively transforms all map keys using the provided function."
  [m fun]
  (let [f (fn [[k v]] [(fun k) v])]
    ;; only apply to maps
    (postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn convert-keys1
  "Transform all map keys at the top level, no recursion."
  [m fun]
  (->> m
       (map (fn [[k v]] [(fun k) v]))
       (into {})))

(defn coll-wrap
  "Given an argument, return it if coll? is true, else wrap it in a list."
  [thing]
  (if (coll? thing)
    thing
    (list thing)))

(defn ->print
  [x]
  (println x)
  x)

(defn combine
  "Recursively merges maps. If vals are not maps, the last non-nil value wins."
  [& vals]
  (if (every? map? vals)
    (apply merge-with combine vals)
    (last (remove nil? vals))))

(defn remove-nil-vals
  "Remove all nil values from a map."
  [a-map]
  (postwalk (fn [x]
              (if (map? x)
                (into {} (remove (comp nil? second) x))
                x)) a-map))
