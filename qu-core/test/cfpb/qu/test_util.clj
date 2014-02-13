(ns cfpb.qu.test-util
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mockreq]
            [cfpb.qu.main :refer [app]]            
            [org.httpkit.server :refer [run-server]]
            [org.httpkit.client :as client]
            [cfpb.qu.main :refer [app]]))

(def port 4545)
(def server (atom nil))
(defn stop-server [] (when @server (@server)))

(defn server-setup
  [test]
  (stop-server)
  (reset! server (run-server app {:port port}))
  (test)
  (stop-server))

(defn GET
  [url]
  (app (mockreq/request :get url)))

(defn does-contain [container contained]
  (let [container (if (sequential? container)
                    (set container)
                    container)]
    (cond
     (not (coll? contained)) (is (contains? container contained))
     (vector? contained) (doseq [e contained] (is (contains? container e)))
     :else (doseq [[k v] contained]
             (is (= (get container k) v))))))

(defn does-not-contain [m1 m2]
  (cond
   (keyword? m2) (is (not (contains? m1 m2)))
   :else (doseq [[k v] m2]
           (is (not (= (get m1 k) v))))))

(defmacro does= [& body]
  `(are [x y] (= x y)
        ~@body))

(defmacro does-not= [& body]
  `(are [x y] (not (= x y))
        ~@body))

(defmacro does-re-match [& body]
  `(are [text pattern] (re-matches pattern text)
        ~@body))

(defmacro does-not-re-match [& body]
  `(are [text pattern] (not (re-matches pattern text))
        ~@body))

(defmacro does-re-find [& body]
  `(are [text pattern] (re-find pattern text)
        ~@body))

(defmacro does-not-re-match [& body]
  `(are [text pattern] (not (re-find pattern text))
        ~@body))
