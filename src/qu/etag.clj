(ns qu.etag
  (:require [digest :refer [md5]])
  (:import (java.io File)))

(defmulti calculate-etag class)
(defmethod calculate-etag String [s] (md5 s))
(defmethod calculate-etag File
  [f]
  (str (.lastModified f) "-" (.length f)))
(defmethod calculate-etag :default
  [_]
  nil)

(defn- not-modified-response [etag]
  {:status 304 :body "" :headers {"etag" etag}})

(defn wrap-etag [handler]
  "Generates an etag header by hashing response body (currently only
supported for string bodies). If the request includes a matching
'if-none-match' header then return a 304."
  (fn [req]
    (let [{body :body
           status :status
           {etag "ETag"} :headers
           :as resp} (handler req)
           if-none-match (get-in req [:headers "if-none-match"])]
      (if (and etag (not= status 304))        
        (if (= etag if-none-match)
          (not-modified-response etag)
          resp)
        (if (and (or (string? body) (instance? File body))
                 (= status 200))
          (let [etag (calculate-etag body)]            
            (if (= etag if-none-match)
              (not-modified-response etag)
              (assoc-in resp [:headers "ETag"] etag)))
          resp)))))
