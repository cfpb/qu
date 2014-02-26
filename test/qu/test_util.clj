(ns qu.test-util
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mockreq]
            [org.httpkit.client :as client]
            [qu.main :as main]
            [qu.app :as app]
            [qu.app.webserver :as webserver]
            [qu.app.mongo :refer [new-mongo]]
            [qu.loader :as loader]
            [com.stuartsierra.component :as component]))

(def port 4545)
(def system (atom nil))
(def app (webserver/get-handler false))
(def test-options (-> (main/default-options)
                      (assoc-in [:http :port] port)))

(defn system-setup
  [test]
  (if @system
    (swap! system component/stop)
    (reset! system (app/new-qu-system test-options)))
  (swap! system component/start)
  (test)
  (swap! system component/stop))

(defn mongo-setup-fn
  [db]
  (fn [test]
    (let [mongo (new-mongo (main/default-mongo-options))]
      (component/start mongo)
      (loader/load-dataset db)
      (test)
      (component/stop mongo))))

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
