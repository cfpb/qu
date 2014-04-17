(ns user)

(def namespaces (atom {}))

(alter-var-root (var clojure.core/load-lib)
                (fn [f]
                  (fn [prefix lib & options]
                    (let [start (. java.lang.System (nanoTime))
                          return (apply f prefix lib options)
                          end (. java.lang.System (nanoTime))
                          ms (/ (double (- end start)) 1000000.0)
                          lib-name (if prefix
                                     (keyword (str prefix "." lib))
                                     (keyword lib))]
                      (swap! namespaces
                             update-in [lib-name] (fnil max ms) ms)
                      return))))

(defn ns-times
  []
  (reverse (sort-by second @namespaces)))

(require '[alembic.still :refer [distill]]
         '[clojure.string :as str]
         '[clojure.pprint :refer [pprint]]
         '[clojure.repl :refer :all]
         '[clojure.tools.namespace.repl :refer [refresh refresh-all set-refresh-dirs]]
         '[com.stuartsierra.component :as component]
         '[qu.main :as main]
         '[qu.app :refer [new-qu-system]]
         '[qu.app.options :refer [inflate-options]]
         '[qu.util :refer :all]
         '[qu.loader :as loader :refer [load-dataset]])

(set-refresh-dirs "src/" "dev/")

(def system (atom nil))

(defn init
  ([] (init {}))
  ([options]
     (reset! system (new-qu-system (combine (main/default-options) options)))))

(defn start
  []
  (swap! system component/start)
  (swap! system assoc :running true))

(defn stop
  []
  (swap! system component/stop)
  (swap! system assoc :running false))

(defn go
  ([] (go {}))
  ([options]
     (init options)
     (start)))

(defn reset
  []
  (stop)
  (refresh :after 'user/go))

(defn load-sample-data
  []
  (if (:running @system)
    (load-dataset "county_taxes")
    (println "System not running")))
