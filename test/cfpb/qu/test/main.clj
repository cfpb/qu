(ns cfpb.qu.test.main
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [cfpb.qu.main :refer [app]]))

(deftest index-url
  (testing "it redirects to /data"
    (let [resp (app (request :get "/"))]
      (is (= (:status resp)
             302))
      (is (= (get-in resp [:headers "Location"]))
          "/data"))))

(deftest data-url
  (testing "it returns successfully"
    (with-redefs [cfpb.qu.data/get-datasets (constantly [])]
      (let [resp (app (request :get "/data"))]
        (is (= (:status resp)
               200))
        (is (= (get-in resp [:headers "Content-Type"])
               "text/html;charset=UTF-8")))

      (let [resp (app (request :get "/data.xml"))]
        (is (= (:status resp)
               200))
        (is (= (get-in resp [:headers "Content-Type"])
               "application/xml;charset=UTF-8"))))))

(deftest dataset-url
  (testing "it returns successfully when the dataset exists"
    (with-redefs [cfpb.qu.data/get-metadata (constantly {})]
      (let [resp (app (request :get "/data/good-dataset"))]
        (is (= (:status resp)
               200))
        (is (= (get-in resp [:headers "Content-Type"])
               "text/html;charset=UTF-8")))
      
      (let [resp (app (request :get "/data/good-dataset.xml"))]
        (is (= (:status resp)
               200))
        (is (= (get-in resp [:headers "Content-Type"])
               "application/xml;charset=UTF-8"))))))

(deftest slice-url
  (testing "it returns successfully when the dataset and slice exist"
    (with-redefs [cfpb.qu.data/get-metadata (constantly {:slices {:whoa {}}})
                  cfpb.qu.query/execute (constantly {:total 0 :size 0 :data []})]      
      (let [resp (app (request :get "/data/good-dataset/slice/whoa"))]
        (is (= (:status resp)
               200))
        (is (= (get-in resp [:headers "Content-Type"])
               "text/html;charset=UTF-8")))
      
      (let [resp (app (request :get "/data/good-dataset/slice/whoa.xml"))]
        (is (= (:status resp)
               200))
        (is (= (get-in resp [:headers "Content-Type"])
               "application/xml;charset=UTF-8")))))

  (testing "it returns a 404 when the dataset does not exist"
    (with-redefs [cfpb.qu.data/get-metadata (constantly nil)]
      (let [resp (app (request :get "/data/bad-dataset/what"))]
        (is (= (:status resp) 404)))))

  (testing "it returns a 404 when the slice does not exist"
    (with-redefs [cfpb.qu.data/get-metadata (constantly {:slices {}})]
      (let [resp (app (request :get "/data/bad-dataset/what"))]
        (is (= (:status resp) 404))))))

;; (run-tests)
