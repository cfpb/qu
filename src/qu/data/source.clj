(ns qu.data.source)

(defprotocol DataSource
  (get-datasets [source]) ;; returns a list of all datasets
  (get-metadata [source dataset]) ;; returns the metadata for a dataset
  (get-concept-data [source dataset concept]) ;; returns the data table for a concept
  (get-results [source query])
  (load-dataset [source definition options]))
