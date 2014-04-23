(ns monger.key-compression
  (:require [clojure.set :refer [map-invert]]
            [digest :refer [md5]]))

;; TODO Explain algorithm

(def select (comp first filter))

(defn- trie-add
  [trie words]
  (reduce
   (fn [trie word]
     (let [word (md5 word)]
       (assoc-in trie (concat word [::val]) word)))
   trie
   words))

(defn- trie-matches
  [trie prefix]
  (letfn [(search [node]
            (mapcat (fn [[k v]]
                      (if (= ::val k) [v] (search v)))
                    node))]
    (search (get-in trie prefix))))

(defn- get-prefixes
  [field]
  (reduce
   (fn [prefixes letter]
     (conj prefixes ((fnil str "") (last prefixes) letter)))
   []
   field))

(defn- get-unique-prefix
  [field trie]
  (let [field (md5 field)
        prefixes (get-prefixes field)]
    (select (fn [prefix] (or (= 1 (count (trie-matches trie prefix)))
                             (= prefix field)))
            prefixes)))

(defn -compression-map
  "Create a map of shortened unique field names from a list of fields."
  [field-list]
  (let [field-list (remove #(= % "_id") (map name field-list))
        field-trie (trie-add {} field-list)]
    (into {}
          (map
           (fn [field]
             [(keyword field) (keyword (get-unique-prefix field field-trie))])
           field-list))))

(def compression-map (memoize -compression-map))

(defn compression-fn
  [field-list]
  (let [comp-map (compression-map field-list)]
    (fn [field]
      (let [field (keyword field)]
        (get comp-map field field)))))

(defn decompression-fn
  [field-list]
  (let [decomp-map (map-invert (compression-map field-list))]
    (fn [field]
      (let [field (keyword field)]
        (get decomp-map field field)))))

