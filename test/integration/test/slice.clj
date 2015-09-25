(ns ^:integration integration.test.slice
  (:require [clojure.test :refer :all]
            [qu.loader :as loader]
            [qu.data :as data]
            [qu.test-util :refer :all]))

(use-fixtures :once (mongo-setup-fn "integration_test"))

(deftest ^:integration test-query-slice-with-no-params
  (testing "it returns successfully as text/html"
    (let [resp (GET "/data/integration_test/slice/incomes")]
      (does= (:status resp) 200)
      (does-contain (:headers resp)
                    {"Content-Type" "text/html;charset=UTF-8" "Vary" "Accept"}))))

(deftest ^:integration test-query-slice-does-not-exist
  (testing "it returns a 404"
    (let [resp (GET "/data/bad-dataset/slice/bad-slice")]
      (does= (:status resp) 404))

    (let [resp (GET "/data/bad-dataset/slice/bad-slice.xml")]
      (does= (:status resp) 404)
      (does-contain (:headers resp)
                    {"Content-Type" "application/xml;charset=UTF-8" "Vary" "Accept"}))))

(deftest ^:integration test-json
  (testing "it returns a content-type of application/json"
    (let [resp (GET "/data/integration_test/slice/incomes.json")]
      (does= (:status resp) 200)
      (does-contain (:headers resp)
                    {"Content-Type" "application/json;charset=UTF-8"}))))

(deftest ^:integration test-jsonp
  (testing "it uses the callback we supply"
    (let [resp (GET "/data/integration_test/slice/incomes.jsonp?$callback=foo")]
      (does= (:status resp) 200)
      (does-contain (:headers resp)
                    {"Content-Type" "text/javascript;charset=UTF-8"})
      (does-re-find (:body resp) #"^foo\(")))

  (testing "it uses 'callback' by default"
    (let [resp (GET "/data/integration_test/slice/incomes.jsonp")]
      (does= (:status resp) 200)
      (does-contain (:headers resp)
                    {"Content-Type" "text/javascript;charset=UTF-8"})
      (does-re-find (:body resp) #"^callback\("))))

(deftest ^:integration test-xml
  (testing "it returns a content-type of application/xml"
    (let [resp (GET "/data/integration_test/slice/incomes.xml")]
      (does= (:status resp) 200)
      (does-contain (:headers resp)
                    {"Content-Type" "application/xml;charset=UTF-8"}))))

(deftest ^:integration test-query-with-error
  (testing "it returns the status code for bad request"
    (let [resp (GET "/data/integration_test/slice/incomes?$where=peanut%20butter")]
      (does= (:status resp) 400))))

;; (run-tests)
