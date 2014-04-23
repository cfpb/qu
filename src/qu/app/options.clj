(ns qu.app.options
  (:require [qu.util :refer :all]
            [schema.core :as s]))

(def HttpOptionsS
  {:view {:api_name s/Str
          :base_url s/Str
          :dev_mode s/Bool          
          (s/optional-key :build_number) s/Str
          (s/optional-key :build_url) s/Str
          (s/optional-key :qu_version) s/Str}
   :ip s/Str
   :port s/Int
   :threads s/Int
   :queue-size s/Int})

(def MongoOptionsS
  (let [database (s/either s/Str s/Keyword)
        conn-uri-s {:uri s/Str}
        conn-hosts-s {:hosts [[(s/one s/Str "ip")
                               (s/one s/Int "port")]]}
        conn-host-s {:host s/Str :port s/Int}]
    {:conn (s/either conn-uri-s
                     conn-hosts-s
                     conn-host-s)
     :options {s/Any s/Any}
     (s/optional-key :auth) (s/maybe {database
                                      [(s/one s/Str "username")
                                       (s/one s/Str "password")]})}))

(def MetricsOptionsS
  (s/either
   {:provider s/Keyword
    :host s/Str
    :port s/Int}
   {}))

(def OptionsS
  {(s/optional-key :dev) s/Bool
   :log {(s/optional-key :file) s/Str
         :level s/Keyword}
   :mongo MongoOptionsS
   :http HttpOptionsS
   :metrics MetricsOptionsS})

(defn inflate-options
  [options]
  
  (let [default {:dev false
                 :http {:ip "0.0.0.0"
                        :port 3000
                        :threads 4
                        :queue-size 20480
                        :view {:base_url ""
                               :api_name "Data API"}}
                 :mongo {:conn {:host "127.0.0.1"
                                :port 27017}
                         :options {:connect-timeout 2000}}
                 :log {:level :info}
                 :metrics {}}
        set-dev-mode (fn [opts]
                       (assoc-in opts
                                 [:http :view :dev_mode]
                                 (:dev opts)))]
    (->> options
         (remove-nil-vals)
         (combine default)
         (set-dev-mode)
         (s/validate OptionsS))))
