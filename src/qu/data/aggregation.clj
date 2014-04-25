(ns qu.data.aggregation
  "This namespace contains functions for generating aggregations
within Mongo."
  (:require [lonocloud.synthread :as ->]
            [qu.data.compression :refer [compress-where field-unzip-fn
                                         field-zip-fn]]
            [qu.util :refer :all]))

(defn- select-to-agg
  "Convert a aggregation map into the Mongo equivalent for the
  $group filter of the aggregation framework. Used in the group
  function. Non-public."
  [field-zip-fn]
  (fn [[alias [agg field]]]
    (if (= agg "count")
      {alias {"$sum" 1}}
      {alias {(str "$" agg) (str "$" (name (field-zip-fn field)))}})))

(defn aggregation-group-args
  "Build the arguments for the group section of the aggregation framework."
  [group aggregations field-zip-fn]
  (let [id (into {} (map (fn [field]
                           (vector field (str "$" (name (field-zip-fn field)))))
                         group))
        aggregations (map (select-to-agg field-zip-fn) aggregations)
        group (apply merge {:_id id} aggregations)]
    group))

(defn aggregation-project-args
  [group aggregations]
  (let [project-map {:_id 0}
        project-map (reduce (fn [project-map field]
                              (assoc project-map field (str "$_id." (name field))))
                            project-map group)
        project-map (reduce (fn [project-map field]
                              (assoc project-map field 1))
                            project-map (keys aggregations))]
    project-map))

(defn generate-agg-query
  [{:keys [to group aggregations filter sort slicedef] :as aggmap}]
  (let [field-zip-fn (if slicedef
                       (field-zip-fn slicedef)
                       identity)
        field-unzip-fn (if slicedef
                         (field-unzip-fn slicedef)
                         identity)
        match (if filter
                (-> filter
                    (compress-where field-zip-fn)
                    (convert-keys name)))
        group-args (aggregation-group-args group aggregations field-zip-fn)
        project-args (aggregation-project-args group aggregations)
        ]
    (-> []
        (->/when match
          (conj {"$match" match}))
        (conj {"$group" group-args})
        (conj {"$project" project-args})
        (->/when sort
          (conj {"$sort" sort}))
        (conj {"$out" to}))))
