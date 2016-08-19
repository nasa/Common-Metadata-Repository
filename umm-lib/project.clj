(defproject nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"
  :description "The UMM (Unified Metadata Model) Library is responsible for defining the common domain
               model for Metadata Concepts in the CMR along with code to parse and generate the
               various dialects of each concept."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/umm-lib"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-spatial-lib "0.1.0-SNAPSHOT"]]

  :plugins [[test2junit "1.2.1"]]

  ;; The ^replace is done to disable the tiered compilation for accurate benchmarks
  ;; See https://github.com/technomancy/leiningen/wiki/Faster
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]
                        [pjstadig/humane-test-output "0.8.1"]
                        [criterium "0.4.4"]
                        [proto-repl "0.3.1"]]
         :jvm-opts ^:replace ["-server"]
                              ;; Uncomment this to enable assertions. Turn off during performance tests.
                              ; "-ea"

                              ;; Use the following to enable JMX profiling with visualvm
                              ;; "-Dcom.sun.management.jmxremote"
                              ;; "-Dcom.sun.management.jmxremote.ssl=false"
                              ;; "-Dcom.sun.management.jmxremote.authenticate=false"
                              ;; "-Dcom.sun.management.jmxremote.port=1098"]
         :source-paths ["src" "dev" "test"]
         :injections [(require 'pjstadig.humane-test-output)
                      (pjstadig.humane-test-output/activate!)]}}
  :aliases { ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]})
