(defproject qu "0.1.0-SNAPSHOT"
  :description "qu is an **in-progress** data platform created by the CFPB to
serve their public data sets."
  :url "https://github.com/cfpb/qu"
  :min-lein-version "2.0.0"
  :source-paths ["src"]
  :plugins [[codox "0.6.4"]
            [lein-cloverage "1.0.2"]
            [lein-environ "0.4.0"]
            [lein-midje "3.0.0"]
            [lein-ring "0.8.2"]
            [lein-embongo "0.2.0"]]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [cheshire "5.0.2"]
                 [clojure-csv/clojure-csv "2.0.0-alpha2"]
                 [com.ebaxt.enlive-partials "0.1.1"]
                 [com.novemberain/monger "1.5.0"]
                 [com.taoensso/timbre "1.5.2"]
                 [compojure "1.1.3"]
                 [enlive "1.1.1"]
                 [environ "0.4.0"]
                 [lib-noir "0.4.9"]
                 [liberator "0.8.0"]
                 [parse-ez "0.3.4"]
                 [ring/ring-core "1.1.6"]
                 [ring/ring-jetty-adapter "1.1.6"]
                 [ring.middleware.mime-extensions "0.2.0"]

                 ;; provisional
                 [lonocloud/synthread "1.0.4"]
                 [slingshot "0.10.3"]
                 ]
  :main cfpb.qu.core
  :ring {:handler cfpb.qu.handler/app
         :init cfpb.qu.handler/init
         :destroy cfpb.qu.handler/destroy}
  :codox {:src-dir-uri "https://github.com/cfpb/qu/blob/master"
          :src-linenum-anchor-prefix "L"}
  :profiles {:dev             
             {:env {:mongo-host "127.0.0.1"
                    :mongo-port 27017}
              :dependencies [[ring-mock "0.1.3"]
                             [midje "1.5.0"]]}})
