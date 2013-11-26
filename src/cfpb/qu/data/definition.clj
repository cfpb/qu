(ns cfpb.qu.data.definition
  "Functions for reading and altering dataset definitions. Includes
  schema for validation."
  (:require [cfpb.qu.util :refer :all]
            [schema.core :as s]
            [clojure.java.io :as io]            
            [cheshire.core :as json]
            [cheshire.factory :as factory]))

(def InfoS {(s/required-key :name) s/String
            (s/optional-key :description) s/String
            (s/optional-key :url) s/String
            s/Keyword s/Any
            })

(def TypeS (s/enum "string" "integer" "date" "dollars" "number"))

(def IndexS (s/either s/String [s/String]))

(defn ref-col-count-eq?
  "Make sure that if you have multiple columns in a reference that you
  have the same number of columns in the id."
  [ref]
  (if (coll? (:column ref))
    (= (count (:column ref)) (count (:id ref)))
    (not (coll? (:id ref)))))

(def ReferenceS (s/both (s/pred ref-col-count-eq?)
                        {(s/required-key :column) (s/either s/String [s/String])
                         (s/required-key :concept) s/String
                         (s/optional-key :id) (s/either s/String [s/String])
                         (s/required-key :value) s/String
                         }))

(def TableSliceS {(s/optional-key :info) {s/Keyword s/String}
                  (s/required-key :type) (s/eq "table")
                  (s/required-key :table) s/String
                  (s/required-key :dimensions) [s/String]
                  (s/required-key :metrics) [s/String]
                  (s/optional-key :indexes) [IndexS]
                  (s/optional-key :references) {s/Keyword ReferenceS}
                  })

(def DerivedSliceS {(s/optional-key :info) {s/Keyword s/String}
                    (s/required-key :type) (s/eq "derived")
                    (s/required-key :slice) s/String
                    (s/required-key :dimensions) [s/String]
                    (s/required-key :metrics) [s/String] ;; TODO remove metrics from here
                    (s/optional-key :indexes) [IndexS]
                    (s/required-key :aggregations) {s/Keyword [s/String]}
                    })

(def SliceS (s/either TableSliceS DerivedSliceS))

(def SimpleConceptS {(s/optional-key :description) s/String
                     (s/optional-key :type) TypeS})
(def TableConceptS (merge SimpleConceptS
                          {(s/required-key :table) s/String
                           (s/required-key :properties) {s/Keyword
                                                         {(s/required-key :type) TypeS
                                                          s/Keyword s/String}}}))
(def ConceptS (s/either SimpleConceptS TableConceptS))

(def ColumnS {(s/optional-key :name) s/String
              (s/optional-key :skip) boolean
              (s/optional-key :type) TypeS
              (s/optional-key :format) s/String})

(def TableS {:sources [s/String]
             :columns {s/Keyword ColumnS}})

(def DataDefinitionS {(s/required-key :info) InfoS
                      (s/required-key :slices) {s/Keyword SliceS}
                      (s/optional-key :concepts) {s/Keyword ConceptS}
                      (s/required-key :tables) {s/Keyword TableS}
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
;; (read-definition (io/resource "datasets/hmda/definition.json"))
;; (s/explain DataDefinitionS)
