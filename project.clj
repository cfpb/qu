(defproject qu "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :source-paths ["src"]
  :plugins [[lein-ring "0.8.2"]
            [lein-kibit "0.0.7"]
            [jonase/eastwood "0.0.2"]
            [codox "0.6.4"]
            [lein-marginalia "0.7.1"]  
            [lein-outdated "1.0.0"]]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.3"]
                 [ring/ring-core "1.1.6"]
                 [ring/ring-jetty-adapter "1.1.6"]
                 [liberator "0.8.0"]
                 [enlive "1.1.1"]
                 [com.ebaxt.enlive-partials "0.1.1"]
                 [com.novemberain/monger "1.4.2"]
                 [cheshire "5.0.2"]
                 [clojure-csv/clojure-csv "2.0.0-alpha2"]
                 [parse-ez "0.3.4"]]
  :main cfpb.qu.core
  :ring {:handler cfpb.qu.core/app}
  :profiles {:dev
             {:dependencies [[ring-mock "0.1.3"]]}})
