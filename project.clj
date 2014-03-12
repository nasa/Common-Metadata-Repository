(defproject cmr-umm-lib "0.1.0-SNAPSHOT"
  :description "The UMM (Unified Metadata Model) Library is responsible for defining the common domain
               model for Metadata Concepts in the CMR along with code to parse and generate the
               various dialects of each concept."
  :url "***REMOVED***projects/CMR/repos/cmr-umm-lib/browse"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/test.check "0.5.7"]
                 [org.clojure/data.xml "0.0.7"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]]

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]

         :source-paths ["src" "dev" "test"]}})


