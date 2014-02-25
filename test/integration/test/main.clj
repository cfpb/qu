(ns ^:integration integration.test.main
  (:require [clojure.test :refer :all]
            [qu.test-util :refer :all]))

(use-fixtures :once (mongo-setup-fn "integration_test"))

(deftest ^:integration test-index-url
  (testing "it redirects to /data"
    (does-contain (GET "/") {:status 302})
    (does-contain (:headers (GET "/"))
                  {"Location" "/data"})))

(deftest ^:integration test-data-url
  (testing "it returns successfully"
    (let [resp (GET "/data")]
      (does= (:status resp) 200)
      (does-contain (:headers resp)
                    {"Content-Type" "text/html;charset=UTF-8"}))

    (let [resp (GET "/data.xml")]
      (does= (:status resp) 200)
      (does-contain (:headers resp)
                    {"Content-Type" "application/xml;charset=UTF-8"}))))

(deftest ^:integration test-dataset-url
  (testing "it returns successfully"
    (let [resp (GET "/data/integration_test")]
      (does= (:status resp) 200)
      (does-contain (:headers resp)
                    {"Content-Type" "text/html;charset=UTF-8"}))
               
    (let [resp (GET "/data/integration_test.xml")]
      (does= (:status resp) 200)
      (does-contain (:headers resp)
                    {"Content-Type" "application/xml;charset=UTF-8"}))))

(deftest ^:integration test-dataset-url-does-not-exist
  (testing "it returns a 404"
    (let [resp (GET "/data/bad_dataset")]
      (does= (:status resp) 404)
      (does-contain (:headers resp)
                    {"Content-Type" "text/html"}))

    (let [resp (GET "/data/bad_dataset.xml")]
      (does= (:status resp) 404)
      (does-contain (:headers resp)
                    {"Content-Type" "application/xml;charset=UTF-8"}))))

;; (run-tests)
