(def build-number (or (System/getenv "BUILD_NUMBER") "handbuilt"))
(def build-url (System/getenv "BUILD_URL"))
(def git-commit (or (System/getenv "GIT_COMMIT")
                    (System/getenv "TRAVIS_COMMIT")))
(def app-url (or (System/getenv "APP_URL") ""))

(defproject qu "0.9.1"
  :description "qu is an **in-progress** data platform created by the CFPB to
serve their public data sets."
  :build-number ~build-number
  :build-url ~build-url
  :git-commit ~git-commit
  :app-url ~app-url
  :url "https://github.com/cfpb/qu"
  :min-lein-version "2.0.0"
  :source-paths ["src"]
  :main cfpb.qu.main
  :repl-options {:init-ns user}
  :plugins [[lein-environ "0.4.0"]
            [lein-midje "3.1.1"]
            [lein-embongo "0.2.1"]
            [slothcfg "1.0.1"]
            [codox "0.6.4"]]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [cheshire "5.2.0"]
                 [clj-statsd "0.3.9"]                 
                 [clj-time "0.6.0"]
                 [clojurewerkz/urly "1.0.0" :exclusions [com.google.guava/guava]]
                 [com.novemberain/monger "1.6.0"]
                 [com.stuartsierra/dependency "0.1.1"]                 
                 [com.taoensso/timbre "2.6.1" :exclusions [expectations]]
                 [compojure "1.1.5" :exclusions [ring/ring-core clout]]
                 [digest "1.4.3"]
                 [environ "0.4.0"]
                 [halresource "0.1.1-20130809.164342-1"]
                 [http-kit "2.1.10"]                 
                 [lib-noir "0.6.8"]
                 [liberator "0.9.0"]
                 [lonocloud/synthread "1.0.5"]
                 [org.clojure/core.cache "0.6.3"]                
                 [org.clojure/data.csv "0.1.2"]
                 [org.codehaus.jsr166-mirror/jsr166y "1.7.0"]                 
                 [parse-ez "0.3.6"]
                 [ring "1.2.0"]                 
                 [ring.middleware.mime-extensions "0.2.0"]
                 [scriptjure "0.1.24"]
                 [slingshot "0.10.3"]                 
                 [stencil "0.3.2"]]
  :jar-exclusions [#"(^|/)\." #"datasets/.*" ]
  :uberjar-exclusions [#"(^|/)\." #"datasets/.*"
                       #"META-INF/.*\.SF" #"META-INF/.*\.[RD]SA"]  
  :slothcfg {:namespace cfpb.qu.project
             :config-source-path "src"}
  :profiles {:uberjar {:aot [cfpb.qu.main]}
             :dev {:source-paths ["dev"]
                   :env {:dev true}
                   :embongo {:version "2.4.5"}
                   :codox {:output-dir "doc/codox"
                           :src-dir-uri "https://github.com/cfpb/qu/blob/master"
                           :src-linenum-anchor-prefix "L"
                           :writer codox-md.writer/write-docs}                   
                   :dependencies [[alembic "0.1.3"]
                                  [clj-http "0.7.6"]
                                  [factual/drake "0.1.4-SNAPSHOT"]                                  
                                  [midje "1.6-SNAPSHOT"]
                                  [midje-junit-formatter "0.1.0-SNAPSHOT"]
                                  [org.clojure/tools.namespace "0.2.4"]
                                  [org.clojure/java.classpath "0.2.1"]
                                  [ring-mock "0.1.5"]
                                  [codox-md "0.2.0"]
                                  ]}
             :integration [:default
                           {:env {:mongo-port 37017
                                  :integration true}
                            :embongo {:port 37017}}]})
