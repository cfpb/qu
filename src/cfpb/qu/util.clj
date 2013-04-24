(ns cfpb.qu.util
  "Utility functions for use throughout Qu."
  (:require [clojure.string :as str]))

(defn ->int
  "Convert strings and integers to integers. A blank string or
anything but a string or integer will return the default value, which
is nil unless specified."
  ([val] (->int val nil))
  ([val default]
     (cond
      (integer? val) val
      (and
       (string? val)
       (not (str/blank? val))) (Integer/parseInt val)
       :default default)))

