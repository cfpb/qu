(ns cfpb.qu.data.aggregation
  "This namespace contains functions for generating aggregations
within Mongo."
  (:require
   [cfpb.qu.data.compression :refer [compress-where field-zip-fn]]
   [cfpb.qu.util :refer :all]
   [taoensso.timbre :as log]   
   [com.reasonr.scriptjure :refer [cljs cljs* js js*]]))

(def ^:private agg-fns
  {:min (js* (fn [ary field]
               (var vals (.map ary (fn [obj] (return (aget obj field)))))
               (return (.apply (aget Math "min") nil vals))))
   :max (js* (fn [ary field]
               (var vals (.map ary (fn [obj] (return (aget obj field)))))
               (return (.apply (aget Math "max") nil vals))))
   :avg (js* (fn [ary field]
               (var vals (.map ary (fn [obj] (return (aget obj field)))))
               (return (.avg Array vals))))
   :median (js* (fn [ary field]
                  (var median (fn [array]
                                (var array (.sort array))
                                (var len (aget array "length"))
                                (if (= len 0)
                                  (return nil))
                                (if (= (% len 2) 0)
                                  (return (/ (+ (aget array (- 1 (/ len 2)))
                                                (aget array (/ len 2)))
                                             2))
                                  (return (aget array (/ (- len 1) 2))))))
                  (var vals (.map ary (fn [obj] (return (aget obj field)))))
                  (return (median vals))))
   :sum (js* (fn [ary field]
               (var vals (.map ary (fn [obj] (return (aget obj field)))))
               (return (.sum Array vals))))
   :count (js* (fn [ary field]
                 (var vals (.map ary (fn [obj] (return (aget obj field)))))
                 (return (.sum Array vals))))})

(defn- generate-map-fn
  ([group-fields agg-fields]
     (generate-map-fn group-fields agg-fields identity))
  ([group-fields agg-fields zip-fn]
     (let [create-map-obj (fn [fields]
                            (reduce (fn [acc [out-field in-field]]
                                      (merge acc {out-field
                                                  (if (number? in-field)
                                                    in-field
                                                    (let [from-field (name (zip-fn in-field))]
                                                      (js* (aget this (clj from-field)))))}))
                                    {} fields))
           map-id (create-map-obj group-fields)
           map-val (create-map-obj agg-fields)]
       (js* (fn [] (emit (clj map-id) (clj map-val)))))))

(defn- generate-reduce-fn
  [aggregations]
  (let [reduce-obj (reduce (fn [acc [out-field [agg in-field]]]
                             (merge acc
                                    {(name out-field)
                                     (js* ((aget aggs (clj agg)) values (clj (name out-field))))}))
                           {} aggregations)]
    (js* (fn [key values]
           (var aggs (clj agg-fns))
           (return (clj reduce-obj))))))

(defn generate-map-reduce*
  "Generate a map with the information needed to run map-reduce inside
  Mongo. This does not convert the map and reduce functions to strings
  with JS in them, so it works for generating something you can paste
  right into Mongo, like so:

  (cljs (generate-map-reduce* {:dataset \"integration_test\"
                               :from \"incomes\"
                               :to \"test1\"
                               :group [:state_abbr]
                               :aggregations {:max_tax_returns [\"max\" \"tax_returns\"]}
                               :slicedef from-slice-definition}))

  If you want map and reduce already stringified, to send through
  Monger, run `generate-map-reduce`."  
  [{:keys [dataset from to group aggregations filter slicedef] :or {aggregations {}}}]
  {:pre [(every? #(not (nil? %)) [dataset from to group aggregations])
         (sequential? group)]}
  (let [field-zip-fn (if slicedef
                       (field-zip-fn slicedef)
                       identity)
        query (if filter
                (-> filter
                    (compress-where field-zip-fn)
                    (convert-keys name))
                {})
        agg-fields (->> aggregations
                        (map (juxt first (comp second second)))
                        (remove #(nil? (second %)))
                        (into {}))
        map-fn (generate-map-fn
                (zipmap group group)
                agg-fields
                field-zip-fn)        
        reduce-fn (generate-reduce-fn aggregations)]
    (array-map :mapreduce (name from)
               :map map-fn
               :reduce reduce-fn
               :out (name to)
               :query query
               :verbose true)))

(defn generate-map-reduce
  "Generates a map with the information needed to run map-reduce
  from Monger."
  [args]
  (let [map-reduce (generate-map-reduce* args)]
    (-> map-reduce
        (update-in [:map] #(cljs %))
        (update-in [:reduce] #(cljs %)))))
