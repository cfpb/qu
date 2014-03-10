(ns ^:integration integration.test.writer
  (:require [clojure.test :refer :all]
            [qu.writer :refer :all]            
            [qu.test-util :refer :all]
            [qu.query :as query]
            [qu.data :as data]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]))

(def db "integration_test")
(def coll "incomes")
(defn tempfile
  []
  (java.io.File/createTempFile "query" ".csv"))

(use-fixtures :once (mongo-setup-fn db))

(deftest ^:integration test-write-csv
  (let [outfile (tempfile)
        writer (io/writer outfile)
        query (query/make-query {:dataset db :slice coll :limit 0})]
    (query->csv query writer)

    (with-open [in (io/reader outfile)]
      (let [csv-data (csv/read-csv in)
            columns (query/columns query)]
        (does= (first csv-data) columns
               (count (rest csv-data))
               (count (:data (query/execute query))))))))
