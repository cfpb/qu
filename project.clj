(defproject qu "0.1.0-SNAPSHOT"
  :description "qu is an **in-progress** data platform created by the CFPB to
serve their public data sets."
  :url "https://github.com/cfpb/qu"
  :source-paths ["src"]
  :plugins [[lein-ring "0.8.2"]
            [codox "0.6.4"]
            [lein-cloverage "1.0.2"]
            [lein-midje "3.0.0"]
            [lein-environ "0.4.0"]]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.3"]
                 [environ "0.4.0"]
                 [ring/ring-core "1.1.6"]
                 [ring/ring-jetty-adapter "1.1.6"]
                 [ring.middleware.logger "0.4.0"]
                 [lib-noir "0.4.6"]
                 [liberator "0.8.0"]
                 [enlive "1.1.1"]
                 [com.ebaxt.enlive-partials "0.1.1"]
                 [com.novemberain/monger "1.4.2"]
                 [cheshire "5.0.2"]
                 [clojure-csv/clojure-csv "2.0.0-alpha2"]
                 [parse-ez "0.3.4"]]
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
