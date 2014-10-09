(defproject nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"
  :description "Contains utilities for retreiving and working with ACLs."
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]]

  :plugins [[lein-test-out "0.3.1"]]

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.5"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :source-paths ["src" "dev" "test"]}})