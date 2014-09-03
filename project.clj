(defproject nasa-cmr/cmr-system-trace-lib "0.1.0-SNAPSHOT"
  :description "Provides library functions for tracing requests throughout the CMR system. Built on
               Twitter Zipkin and clj-zipkin."
  :url "***REMOVED***projects/CMR/repos/cmr-system-trace-lib/browse"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]

                ;; clj-zipkin macros don't work very well. It's included mostly for some utility
                ;; functions, and the thrift handling.
                [clj-zipkin "0.1.3" :exclusions [ch.qos.logback/logback-classic
                                                 log4j
                                                 org.slf4j/slf4j-log4j12]]
                ;; Turn off the logging noise
                [com.dadrox/quiet-slf4j "0.1"]]

  :plugins [[lein-test-out "0.3.1"]]


  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :source-paths ["src" "dev" "test"]}})


