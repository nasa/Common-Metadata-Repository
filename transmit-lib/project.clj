(defproject nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"
  :description "The Transmit Library is responsible for defining the common transmit
                libraries that invoke services within the CMR projects."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/transmit-lib"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [clj-http "1.0.1"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [prismatic/schema "1.0.0"]
                 [org.clojure/data.csv "0.1.2"]]

  :plugins [[lein-test-out "0.3.1"]]

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]

         :source-paths ["src" "dev" "test"]}})


