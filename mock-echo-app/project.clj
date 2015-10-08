(defproject nasa-cmr/cmr-mock-echo-app "0.1.0-SNAPSHOT"
  :description "Mocks out the ECHO REST API."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/mock-echo-app"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
                 [compojure "1.4.0"]
                 [ring/ring-core "1.4.0" :exclusions [clj-time]]
                 [ring/ring-json "0.4.0"]]
  :plugins [[lein-test-out "0.3.1"]]
  :repl-options {:init-ns user}
  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]
                        [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]]
         :source-paths ["src" "dev" "test"]}
   :uberjar {:main cmr.mock-echo.runner
             :aot :all}})


