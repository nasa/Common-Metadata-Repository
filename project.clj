(defproject nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"
  :description "The Transmit Library is responsible for defining the common transmit
                libraries that invoke services within the CMR projects."
  :url "***REMOVED***projects/CMR/repos/cmr-transmit-lib/browse"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-http "0.9.1"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-system-trace-lib "0.1.0-SNAPSHOT"]]

  :plugins [[lein-test-out "0.3.1"]]

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]

         :source-paths ["src" "dev" "test"]}})


