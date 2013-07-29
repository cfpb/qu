(ns cfpb.qu.urls
  (:require [clojure.string :as str]))

(defn index-path
  ([] "/data")
  ([ext] (str (index-path) "." ext)))

(defn dataset-path
  ([dataset] (str "/data/" dataset))
  ([dataset ext] (str (dataset-path dataset) "." ext)))

(defn slice-path
  ([dataset slice] (str/join "/" ["/data" dataset "slice" slice]))
  ([dataset slice ext] (str (slice-path dataset slice) "." ext)))

(defn concept-path
  ([dataset concept] (str/join "/" ["/data" dataset "concept" concept]))
  ([dataset concept ext] (str (concept-path dataset concept) "." ext)))

(defn concepts-path
  ([dataset] (str/join "/" ["/data" dataset "concepts"]))
  ([dataset ext] (str (concepts-path dataset) "." ext)))
