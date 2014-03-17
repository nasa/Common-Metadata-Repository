(defproject nasa-cmr/cmr-system-trace-lib "0.1.0-SNAPSHOT"
  :description "Provides library functions for tracing requests throughout the CMR system. Built on
               Twitter Zipkin and clj-zipkin."
  :url "***REMOVED***projects/CMR/repos/cmr-system-trace-lib/browse"
  :dependencies [[org.clojure/clojure "1.5.1"]

                ;; clj-zipkin macros don't work very well. It's included mostly for some utility
                ;; functions, and the thrift handling.
                [clj-zipkin "0.1.1"]
                [thrift-clj "0.2.1"]
                [clj-scribe "0.3.1"]
                [clj-time "0.6.0"]]


  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :source-paths ["src" "dev" "test"]}})


