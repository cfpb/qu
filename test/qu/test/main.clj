(ns qu.test.main
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [qu.test-util :refer [GET]]))

(deftest index-url
  (testing "it redirects to /data"
    (let [resp (GET "/")]
      (is (= (:status resp)
             302))
      (is (= (get-in resp [:headers "Location"]))
          "/data"))))

(deftest data-url
  (testing "it returns successfully"
    (with-redefs [qu.data/get-datasets (constantly [])]
      (let [resp (GET "/data")]
        (is (= (:status resp)
               200))
        (is (= (get-in resp [:headers "Content-Type"])
               "text/html;charset=UTF-8")))

      (let [resp (GET "/data.xml")]
        (is (= (:status resp)
               200))
        (is (= (get-in resp [:headers "Content-Type"])
               "application/xml;charset=UTF-8"))))))

(deftest dataset-url
  (testing "it returns successfully when the dataset exists"
    (with-redefs [qu.data/get-metadata (constantly {})]
      (let [resp (GET "/data/good-dataset")]
        (is (= (:status resp)
               200))
        (is (= (get-in resp [:headers "Content-Type"])
               "text/html;charset=UTF-8")))
      
      (let [resp (GET "/data/good-dataset.xml")]
        (is (= (:status resp)
               200))
        (is (= (get-in resp [:headers "Content-Type"])
               "application/xml;charset=UTF-8"))))))

(deftest slice-url
  (testing "it returns successfully when the dataset and slice exist"
    (with-redefs [qu.data/get-metadata (constantly {:slices {:whoa {}}})
                  qu.query/execute (constantly {:total 0 :size 0 :data []})]      
      (let [resp (GET "/data/good-dataset/slice/whoa")]
        (is (= (:status resp)
               200))
        (is (= (get-in resp [:headers "Content-Type"])
               "text/html;charset=UTF-8")))
      
      (let [resp (GET "/data/good-dataset/slice/whoa.xml")]
        (is (= (:status resp)
               200))
        (is (= (get-in resp [:headers "Content-Type"])
               "application/xml;charset=UTF-8")))))

  (testing "it returns a 404 when the dataset does not exist"
    (with-redefs [qu.data/get-metadata (constantly nil)]
      (let [resp (GET "/data/bad-dataset/what")]
        (is (= (:status resp) 404)))))

  (testing "it returns a 404 when the slice does not exist"
    (with-redefs [qu.data/get-metadata (constantly {:slices {}})]
      (let [resp (GET "/data/bad-dataset/what")]
        (is (= (:status resp) 404))))))

;; (run-tests)
