(defproject nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"
  :description "Contains utilities for retreiving and working with ACLs."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/acl-lib"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]]

  :plugins [[test2junit "1.2.1"]]

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :source-paths ["src" "dev" "test"]}}
  :aliases {;; Alias to test2junit for consistency with lein-test-out
             "test-out" ["test2junit"]})
