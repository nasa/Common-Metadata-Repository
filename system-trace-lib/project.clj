(defproject nasa-cmr/cmr-system-trace-lib "0.1.0-SNAPSHOT"
  :description "Provides library functions for creating context maps."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/system-trace-lib"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]]

  :plugins [[lein-test-out "0.3.1"]]


  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :source-paths ["src" "dev" "test"]}})


