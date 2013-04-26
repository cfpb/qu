(ns cfpb.qu.urls
  (:require [clojure.string :as str]))

(defn index-path
  ([] "/data")
  ([ext] (str (index-path) "." ext)))

(defn dataset-path
  ([dataset] (str "/data/" dataset))
  ([dataset ext] (str (dataset-path dataset) "." ext)))

(defn slice-path
  ([dataset slice] (str/join "/" ["/data" dataset slice]))
  ([dataset slice ext] (str (slice-path dataset slice) "." ext)))
