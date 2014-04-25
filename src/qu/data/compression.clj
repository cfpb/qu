(ns qu.data.compression
  "Functions for compressing and uncompressing data going into and
coming out of Mongo."
  (:require [clojure.walk :refer [postwalk]]
            [monger.key-compression :as mzip]
            [qu.metrics :as metrics]
            [qu.util :refer :all]))

(defn- slice-columns
  [slicedef]
  (concat (:dimensions slicedef) (:metrics slicedef)))

(defn field-zip-fn
  "Given a slice definition, return a function that will compress
  field names."
  [slicedef]
  (let [fields (slice-columns slicedef)]
    (metrics/with-timing "queries.fields.zip"
      (mzip/compression-fn fields))))

(defn field-unzip-fn
  "Given a slice definition, return a function that will decompress
  field names."
  [slicedef]
  (let [fields (slice-columns slicedef)]
    (metrics/with-timing "queries.fields.unzip"
      (mzip/decompression-fn fields))))

(defn compress-fields
  [fields zipfn]
  (convert-keys fields zipfn))

(defn compress-where
  [where zipfn]
  (let [f (fn [[k v]] (if (keyword? k) [(zipfn k) v] [k v]))]
    ;; only apply to maps
    (postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) where)))

(defn compress-find
  [find-map zipfn]
  (-> find-map
      (update-in [:query] compress-where zipfn)
      (update-in [:fields] convert-keys zipfn)
      (update-in [:sort] convert-keys zipfn)))
