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
                 [cheshire "5.1.1"]
                 [org.clojure/data.csv "0.1.2"]
                 [clojurewerkz/urly "1.0.0"]
                 [com.novemberain/monger "1.5.0"]
                 [com.novemberain/validateur "1.4.0"]
                 [com.taoensso/timbre "1.6.0"]
                 [compojure "1.1.3"]
                 [environ "0.4.0"]
                 [halresource "0.1.0-SNAPSHOT"]
                 [lib-noir "0.5.0"]
                 [liberator "0.8.0"]
                 [lonocloud/synthread "1.0.4"]
                 [parse-ez "0.3.4"]
                 [ring.middleware.mime-extensions "0.2.0"]
                 [ring/ring-core "1.1.6"]
                 [ring/ring-jetty-adapter "1.1.6"]
                 [stencil "0.3.2"]
                 
                 ;; provisional
                 [slingshot "0.10.3"]
                 ]
  :main cfpb.qu.core
  :ring {:handler cfpb.qu.handler/app
         :init cfpb.qu.handler/init
         :destroy cfpb.qu.handler/destroy}
  :codox {:src-dir-uri "https://github.com/cfpb/qu/blob/master"
          :src-linenum-anchor-prefix "L"
          :output-dir "doc/codox"}
  :profiles {:dev
             {:env {:mongo-host "127.0.0.1"
                    :mongo-port 27017}
              :embongo {:version "2.4.3"}
              :dependencies [[ring-mock "0.1.3"]
                             [midje "1.6-SNAPSHOT"]]}
             :integration [:dev
              {:env {:mongo-port 37017
                     :integration true}
               :embongo {:port 37017}}]})
