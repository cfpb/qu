(ns qu.app.mongo
  (:require [com.stuartsierra.component :as component]
            [monger.core :as mongo]
            [qu.util :refer :all]
            [taoensso.timbre :as log]))

(defn authenticate-mongo
  [auth]
  (doseq [[db [username password]] auth]
    (mongo/authenticate (mongo/get-db (name db))
                        username
                        (.toCharArray password))))

(defn connect-mongo
  [{:keys [uri hosts host port] :as conn} options auth]  
  (let [options (apply-kw mongo/mongo-options options)
        connection 
        (cond
         uri (try (mongo/connect-via-uri! uri)
                  (catch Exception e
                    (log/error "The Mongo URI specified is invalid.")))
         hosts (let [addresses (map #(apply mongo/server-address %) hosts)]
                 (mongo/connect! addresses options))
         :else (mongo/connect! (mongo/server-address host port) options))]
    (if (map? auth)
      (authenticate-mongo auth))
    connection))

(defn disconnect-mongo
  []
  (when (bound? #'mongo/*mongodb-connection*)
    (mongo/disconnect!)))

(defrecord Mongo [conn options auth]
  component/Lifecycle
  
  (start [component]
    (connect-mongo conn options auth)
    component)

  (stop [component]
    (disconnect-mongo)
    component))

(defn new-mongo [options]
  (map->Mongo options))
