(ns cfpb.qu.util
  "Utility functions for use throughout Qu."
  (:require [clojure.string :as str]))

(defn str+
  [& xs]
  (str/join (map name xs)))

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

(defn first-or-identity
  "If the argument is a collection, return the first element in the
  collection, else return the argument."
  [thing]
  (if (coll? thing)
    (first thing)
    thing))
