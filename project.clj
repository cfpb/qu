(def build-number (or (System/getenv "BUILD_NUMBER") "handbuilt"))
(def build-url (System/getenv "BUILD_URL"))
(def git-commit (or (System/getenv "GIT_COMMIT")
                    (System/getenv "TRAVIS_COMMIT")))
(def app-url (or (System/getenv "APP_URL") ""))
(def statsd-host (or (System/getenv "STATSD_HOST") nil))
(def statsd-port (or (System/getenv "STATSD_PORT") 8125))

(defproject qu "0.1.0-SNAPSHOT"
  :description "qu is an **in-progress** data platform created by the CFPB to
serve their public data sets."
  :build-number ~build-number
  :build-url ~build-url
  :git-commit ~git-commit
  :app-url ~app-url
  :statsd-host ~statsd-host
  :statsd-port ~statsd-port
  :url "https://github.com/cfpb/qu"
  :min-lein-version "2.0.0"
  :source-paths ["src"]
  :main cfpb.qu.main
  :aot [cfpb.qu.main]
  :repl-options {:init-ns user}
  :plugins [[lein-environ "0.4.0"]
            [lein-midje "3.1.1"]
            [lein-embongo "0.2.1"]
            [slothcfg "1.0.1"]]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [cheshire "5.2.0"]
                 [clj-statsd "0.3.9"]                 
                 [clj-time "0.5.1"]
                 [clojurewerkz/urly "1.0.0"]
                 [com.novemberain/monger "1.6.0"]
                 [com.stuartsierra/dependency "0.1.1"]                 
                 [com.taoensso/timbre "2.5.0"]
                 [compojure "1.1.5"]
                 [digest "1.4.3"]
                 [environ "0.4.0"]
                 [factual/drake "0.1.4-SNAPSHOT"]
                 [halresource "0.1.1-SNAPSHOT"]
                 [http-kit "2.1.9"]                 
                 [lib-noir "0.6.8"]
                 [liberator "0.9.0"]
                 [lonocloud/synthread "1.0.5"]
                 [org.clojure/data.csv "0.1.2"]
                 [org.codehaus.jsr166-mirror/jsr166y "1.7.0"]                 
                 [parse-ez "0.3.6"]
                 [ring "1.2.0"]                 
                 [ring.middleware.mime-extensions "0.2.0"]
                 [stencil "0.3.2"]
                 ]
  :env {:mongo-host "127.0.0.1"
        :mongo-port 27017
        :http-ip "127.0.0.1"
        :http-port 3000
        :http-threads 4
        :http-queue-size 20480
        :dev false
        :integration false}
  :jar-exclusions [#"(^|/)\." #"datasets/.*" ]
  :uberjar-exclusions [#"(^|/)\." #"datasets/.*"
                       #"META-INF/.*\.SF" #"META-INF/.*\.[RD]SA"]  
  :slothcfg {:namespace cfpb.qu.project
             :config-source-path "src"}
  :profiles {:dev {:source-paths ["dev"]
                   :env {:dev true}
                   :embongo {:version "2.4.5"}
                   :dependencies [[alembic "0.1.3"]
                                  [clj-http "0.7.6"]   
                                  [midje "1.6-SNAPSHOT"]
                                  [midje-junit-formatter "0.1.0-SNAPSHOT"]
                                  [org.clojure/tools.namespace "0.2.4"]
                                  [org.clojure/java.classpath "0.2.1"]
                                  [ring-mock "0.1.5"]                                  
                                  ]}
             :integration [:default
                           {:env {:mongo-port 37017
                                  :integration true}
                            :embongo {:port 37017}}]})
