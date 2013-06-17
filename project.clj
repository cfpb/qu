(def build-number (or (System/getenv "BUILD_NUMBER") "handbuilt"))
(def build-url (System/getenv "BUILD_URL"))
(def git-commit (or (System/getenv "GIT_COMMIT")
                    (System/getenv "TRAVIS_COMMIT")))

(defproject qu "0.1.0-SNAPSHOT"
  :description "qu is an **in-progress** data platform created by the CFPB to
serve their public data sets."
  :build-number ~build-number
  :build-url ~build-url
  :git-commit ~git-commit
  :url "https://github.com/cfpb/qu"
  :min-lein-version "2.0.0"
  :source-paths ["src"]
  :plugins [[codox "0.6.4"]
            [lein-cloverage "1.0.2"]
            [lein-environ "0.4.0"]
            [lein-midje "3.0.0"]
            [lein-ring "0.8.2"]
            [lein-embongo "0.2.0"]
            [configleaf "0.4.6"]]
  :hooks [configleaf.hooks]  
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [cheshire "5.2.0"]
                 [clj-time "0.5.1"]
                 [clojurewerkz/urly "1.0.0"]
                 [com.novemberain/monger "1.5.0"]
                 [com.stuartsierra/dependency "0.1.1"]                 
                 [com.taoensso/timbre "2.0.1"]
                 [compojure "1.1.3"]
                 [environ "0.4.0"]
                 [factual/drake "0.1.4-SNAPSHOT"]
                 [halresource "0.1.0-SNAPSHOT"]
                 [lib-noir "0.6.0"]
                 [liberator "0.9.0"]
                 [lonocloud/synthread "1.0.4"]
                 [org.clojure/data.csv "0.1.2"]
                 [parse-ez "0.3.4"]
                 [ring.middleware.mime-extensions "0.2.0"]
                 [ring/ring-core "1.1.6"]
                 [ring/ring-jetty-adapter "1.1.6"]
                 [stencil "0.3.2"]
                 
                 ;; provisional
                 [slingshot "0.10.3"]
                 ]
  :ring {:handler cfpb.qu.handler/app
         :init cfpb.qu.handler/init
         :destroy cfpb.qu.handler/destroy
         :war-exclusions [#"(^|/)\." #"datasets.*" #".*javax.servlet-2.5.0.*" #".*appengine-api-*" #".*aws-java-sdk-*"]}
  :codox {:src-dir-uri "https://github.com/cfpb/qu/blob/master"
          :src-linenum-anchor-prefix "L"
          :output-dir "doc/codox"}
  :configleaf {:namespace cfpb.qu.project}
  :profiles {:dev
             {:source-paths ["dev"]
              :env {:mongo-host "127.0.0.1"
                    :mongo-port 27017}
              :embongo {:version "2.4.3"}
              :dependencies [[ring-mock "0.1.3"]
                             [midje "1.6-alpha2"]
                             [midje-junit-formatter "0.1.0-SNAPSHOT"]
                             [org.clojure/tools.namespace "0.2.3"]
                             [org.clojure/java.classpath "0.2.0"]
                             [alembic "0.1.0"]]}
             :integration [:dev
              {:env {:mongo-port 37017
                     :integration true}
               :dependencies [[clojure-complete "0.2.3"]
                              [org.clojure/tools.nrepl "0.2.3"]]
               :embongo {:port 37017}}]})
