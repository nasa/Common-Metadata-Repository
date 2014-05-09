(defproject cmr-spatial-lib "0.1.0-SNAPSHOT"
  :description "A spatial library for the CMR."
  :url "***REMOVED***projects/CMR/repos/cmr-spatial-lib/browse"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/test.check "0.5.7"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]]

  :plugins [[lein-test-out "0.3.1"]]

  :global-vars {*warn-on-reflection* true}

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]

         :source-paths ["src" "dev" "test"]}})


