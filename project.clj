(defproject qu "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "https://github.com/cfpb/qu"
  :source-paths ["src"]
  :plugins [[lein-ring "0.8.2"]
            [codox "0.6.4"]
            [lein-cloverage "1.0.2"]
            [lein-midje "3.0.0"]]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.3"]
                 [ring/ring-core "1.1.6"]
                 [ring/ring-jetty-adapter "1.1.6"]
                 [liberator "0.8.0"]
                 [enlive "1.1.1"]
                 [com.ebaxt.enlive-partials "0.1.1"]
                 [com.novemberain/monger "1.4.2"]
                 [com.novemberain/validateur "1.4.0"]
                 [cheshire "5.0.2"]
                 [clojure-csv/clojure-csv "2.0.0-alpha2"]
                 [parse-ez "0.3.4"]]
  :main cfpb.qu.core
  :ring {:handler cfpb.qu.core/app}
  :codox {:src-dir-uri "https://github.com/cfpb/qu/blob/master"
          :src-linenum-anchor-prefix "L"}
  :profiles {:dev
             {:dependencies [[ring-mock "0.1.3"]
                             [midje "1.5.0"]]}})
