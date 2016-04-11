(def build-number (or (System/getenv "BUILD_NUMBER") "handbuilt"))
(def build-url (System/getenv "BUILD_URL"))
(def git-commit (or (System/getenv "GIT_COMMIT")
                    (System/getenv "TRAVIS_COMMIT")))

(defproject qu/qu-core "1.1.12"
  :description "qu is a data platform created by the CFPB to serve their public data sets."
  :license {:name "Public Domain"}
  :scm {:name "git"
        :url "https://github.com/cfpb/qu.git"}
  :build-number ~build-number
  :build-url ~build-url
  :git-commit ~git-commit
  :url "https://github.com/cfpb/qu"
  :min-lein-version "2.0.0"
  :source-paths ["src"]
  :main ^:skip-aot qu.main  
  :repl-options {:init-ns user
                 :timeout 600000}
  :plugins [[lein-environ "0.4.0"]
            [lein-embongo "0.2.2"]
            [lein-cloverage "1.0.2"]            
            [test2junit "1.0.1"]            
            [slothcfg "1.0.1"]
            [codox "0.6.4"]]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [caribou/antlers "0.6.1"]                 
                 [cheshire "5.3.1"]
                 [clj-statsd "0.3.10"]                 
                 [clj-time "0.7.0"]
                 [clojurewerkz/route-one "1.1.0"]                 
                 [clojurewerkz/urly "2.0.0-alpha5" :exclusions [com.google.guava/guava]]
                 [com.novemberain/monger "1.7.0"]
                 [com.stuartsierra/component "0.2.1"]                 
                 [com.stuartsierra/dependency "0.1.1"]
                 [com.taoensso/timbre "3.1.6" :exclusions [expectations]]
                 [compojure "1.1.6" :exclusions [ring/ring-core]]
                 [digest "1.4.4"]
                 [environ "0.5.0"]
                 [halresource "0.1.1-20130809.164342-1"]
                 [http-kit "2.1.18"]
                 [liberator "0.11.0"]
                 [lonocloud/synthread "1.0.5"]
                 [me.raynes/fs "1.4.5"]
                 [org.clojure/core.cache "0.6.3"]                
                 [org.clojure/data.csv "0.1.2"]
                 [org.clojure/data.json "0.2.4"]
                 [org.codehaus.jsr166-mirror/jsr166y "1.7.0"]                 
                 [parse-ez "0.3.6"]
                 [prismatic/schema "0.2.1"]
                 [ring "1.2.2"]                 
                 [ring.middleware.mime-extensions "0.2.0"]
                 [ring-middleware-format "0.3.2"]
                 [scriptjure "0.1.24"]
                 ]
  :aliases {"inttest" ["with-profile" "integration" "embongo" "test"]
            "jenkins" ["with-profile" "integration" "embongo" "test2junit"]
            "coverage" ["with-profile" "integration" "embongo" "cloverage"]
            "vagrant-repl" ["repl" ":start" ":host"  "0.0.0.0" ":port" "5678"]}
  :jar-exclusions [#"(^|/)\." #"datasets/.*" ]
  :uberjar-exclusions [#"(^|/)\." #"datasets/.*"
                       #"META-INF/.*\.SF" #"META-INF/.*\.[RD]SA"]  
  :slothcfg {:namespace qu.project
             :config-source-path "src"}
  :test-selectors {:default (fn [t] (not (:integration t)))
                   :all (constantly true)}
  :test2junit-output-dir "test-results"  
  :profiles {:uberjar {:aot [#"qu\.(?!loader)" monger.key-compression]
                       :env {:dev false}}
             :test {:injections [(taoensso.timbre/set-level! :error)]}
             :dev {:source-paths ["dev"]
                   :env {:dev true}
                   :embongo {:version "3.0.6"}
                   :codox {:output-dir "doc/codox"
                           :src-dir-uri "https://github.com/cfpb/qu/blob/master"
                           :src-linenum-anchor-prefix "L"
                           :writer codox-md.writer/write-docs}                   
                   :dependencies [[alembic "0.2.1"]
                                  [clj-http "0.9.1"]
                                  [factual/drake "0.1.5-SNAPSHOT"]
                                  [org.clojure/tools.namespace "0.2.4"]
                                  [org.clojure/java.classpath "0.2.2"]
                                  [ring-mock "0.1.5"]
                                  [codox-md "0.2.0"]
                                  ]}
             :integration [:default
                           {:test-selectors {:default (constantly true)}
                            :env {:mongo-port 37017
                                  :integration true}
                            :embongo {:port 37017}}]})

