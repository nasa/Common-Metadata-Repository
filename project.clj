(defproject nasa-cmr/cmr-mock-echo-app "0.1.0-SNAPSHOT"
  :description "Mocks out the ECHO REST API."
  :url "***REMOVED***projects/CMR/repos/cmr-mock-echo-app/browse"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-system-trace-lib "0.1.0-SNAPSHOT"]
                 [compojure "1.1.6"]
                 [ring/ring-core "1.2.2"]
                 [ring/ring-json "0.3.1"]
                 [org.clojure/tools.reader "0.8.4"]
                 [org.clojure/tools.cli "0.3.1"]]
  :plugins [[lein-test-out "0.3.1"]]
  :repl-options {:init-ns user}
  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :source-paths ["src" "dev" "test"]}
   :uberjar {:main cmr.mock-echo.runner
             :aot :all}})


