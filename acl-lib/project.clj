(defproject nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"
  :description "Contains utilities for retreiving and working with ACLs."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/acl-lib"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.3.2"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]]

  :plugins [[lein-test-out "0.3.1"]]

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.10"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :source-paths ["src" "dev" "test"]}})
