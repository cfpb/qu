(ns qu.writer
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [qu.data :as data]
            [qu.query :as query]))

(defn gzip-writer
  [filename]
  (-> filename
      io/output-stream
      java.util.zip.GZIPOutputStream.
      io/writer))

(defn query->csv
  [query writer]
  (let [query (query/prepare query)
        results (query/execute query)
        columns (query/columns query)
        data (:data results)
        rows (data/get-data-table data columns)]
    (if (not (:computing results))
      (with-open [w writer]
        (csv/write-csv w (vector columns))
        (csv/write-csv w rows)))))
