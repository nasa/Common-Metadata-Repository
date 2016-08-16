(defproject nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"
  :description "Library containing application services code common to multiple CMR applications."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/common-app-lib"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.5.1"]
                 [ring/ring-core "1.5.0"]
                 [ring/ring-json "0.4.0"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-elastic-utils-lib "0.1.0-SNAPSHOT"]

                 ;; Markdown generator needed for API documentation
                 [org.pegdown/pegdown "1.6.0"]]

  :plugins [[test2junit "1.2.1"]]

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :source-paths ["src" "dev" "test"]}}
  :aliases {;; Alias to test2junit for consistency with lein-test-out
             "test-out" ["test2junit"]})
