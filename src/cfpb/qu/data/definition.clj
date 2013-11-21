(ns cfpb.qu.data.definition
  "Functions for reading and altering dataset definitions. Includes
  schema for validation."
  (:require [cfpb.qu.util :refer :all]
            [schema.core :as s]
            [clojure.java.io :as io]            
            [cheshire.core :as json]
            [cheshire.factory :as factory]))

(def IndexS (s/either s/String [s/String]))

(def TableSliceS {(s/required-key :type) (s/eq "table")
                  (s/required-key :table) s/String
                  (s/required-key :dimensions) [s/String]
                  (s/required-key :metrics) [s/String]
                  (s/optional-key :indexes) [IndexS]
                  (s/optional-key :references) s/Any ;; TODO
                  })

(def DerivedSliceS {(s/required-key :type) (s/eq "derived")
                    (s/required-key :slice) s/String
                    (s/required-key :dimensions) [s/String]
                    (s/required-key :metrics) [s/String] ;; TODO remove metrics from here
                    (s/optional-key :indexes) [IndexS]
                    (s/required-key :aggregations) {s/Keyword [s/String]}
                    })

(def SliceS (s/either TableSliceS DerivedSliceS))

(def DataDefinitionS {(s/required-key :info) {s/Keyword s/Any} ;; TODO
                      (s/required-key :slices) {s/Keyword SliceS}
                      (s/optional-key :concepts) {s/Keyword s/Any} ;; TODO
                      (s/required-key :tables) {s/Keyword s/Any} ;; TODO
                      })

(defn read-definition
  "Read the definition of a dataset from disk or URL."
  [f]
  (binding [factory/*json-factory* (factory/make-json-factory {:allow-comments true})]
    (s/validate DataDefinitionS
                (-> (slurp f)
                    (json/parse-string true)))))

(defn dimensions
  "Get the list of dimensions for a slice."
  [definition slice]
  (map keyword (get-in definition [:slices slice :dimensions])))

(defn metrics
  "Get the list of metrics for a slice."
  [definition slice]
  (let [slicedef (get-in definition [:slices slice])]
    (if (= (:type slicedef) "table")
      (map keyword (:metrics slicedef))
      (keys (:aggregations slicedef)))))

(defn fields
  "Get the list of fields for a slice."
  [definition slice]
  (apply concat ((juxt dimensions metrics) definition slice)))

;; (read-definition (io/resource "datasets/integration_test/definition.json"))
