(defproject nasa-cmr/cmr-spatial-lib "0.1.0-SNAPSHOT"
  :description "A spatial library for the CMR."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/spatial-lib"

  ;; Required to obtain the Jafama library which isn't in public maven repos
  :repositories [["releases" "http://devrepo1.dev.echo.nasa.gov/data/dist/projects/echo/mavenrepo/"]]

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]

                 ; Fast math library for atan2, acos, asin, etc
                 [net.jafama/jafama "2.1.0"]

                 ;; Matrix multiplication
                 [net.mikera/core.matrix "0.42.0" :exclusions [org.clojure/clojure]]

                 ;; Fast vectors
                 ;; I could not update this past 0.28.0 without a failure in code when trying to do
                 ;; a matrix multiply with two matrices. We should test updating this in the future
                 ;; when doing updates to see if it's been fixed or take a closer look at the code
                 ;; to make sure it's doing the right thing.
                 [net.mikera/vectorz-clj "0.28.0"]

                 ;; allows enable and disable when assertions run by jvm flags.
                 ;; Can skip assertions for better performance
                 [pjstadig/assertions "0.1.0"]

                 ;; Helps prevent auto boxing when performing math in Clojure
                 [primitive-math "0.1.4"]

                 ;; Added for combinations function
                 [org.clojure/math.combinatorics "0.1.1"]]


  :plugins [[test2junit "1.2.1"]]

  :global-vars {*warn-on-reflection* true}

  ;; The ^replace is done to disable the tiered compilation for accurate benchmarks
  ;; See https://github.com/technomancy/leiningen/wiki/Faster
  :jvm-opts ^:replace ["-server"
                       ;; Uncomment this to enable assertions. Turn off during performance tests.
                       ; "-ea"

                       ;; Use the following to enable JMX profiling with visualvm
                       "-Dcom.sun.management.jmxremote"
                       "-Dcom.sun.management.jmxremote.ssl=false"
                       "-Dcom.sun.management.jmxremote.authenticate=false"
                       "-Dcom.sun.management.jmxremote.port=1098"]



  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]
                        [criterium "0.4.3"]
                        [proto-repl "0.1.2"]
                        [pjstadig/humane-test-output "0.7.0"]]
         :injections [(require 'pjstadig.humane-test-output)
                      (pjstadig.humane-test-output/activate!)]
         :source-paths ["src" "dev" "test"]}}
  :aliases { ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]})
