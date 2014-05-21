(defproject nasa-cmr/cmr-spatial-lib "0.1.0-SNAPSHOT"
  :description "A spatial library for the CMR."
  :url "***REMOVED***projects/CMR/repos/cmr-spatial-lib/browse"

  ;; Required to obtain the Jafama library which isn't in public maven repos
  :repositories [["releases" "http://devrepo1.dev.echo.nasa.gov/data/dist/projects/echo/mavenrepo/"]]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]

                 ; Fast math library for atan2, acos, asin, etc
                 ;; Available in devrepo1 repository. Must be on vpn and run "lein deps".
                 ;; http://sourceforge.net/projects/jafama/?source=dlp
                 ;; Note that this has to be manually installed in the maven repo. It must be
                 ;; downloaded from the source forge and installed. lein ancient will not be able
                 ;; to detect when this library has been updated.
                 [jafama/jafama "2.1"]

                 ;; allows enable and disable when assertions run by jvm flags.
                 ;; Can skip assertions for better performance
                 [pjstadig/assertions "0.1.0"]

                 ;; Helps prevent auto boxing when performing math in Clojure
                 [primitive-math "0.1.3"]

                 ;; visualize spatial areas
                 [nasa-cmr/cmr-vdd-spatial-viz "0.1.0-SNAPSHOT"]

                 ;; Added for combinations function
                 [org.clojure/math.combinatorics "0.0.7"]]


  :plugins [[lein-test-out "0.3.1"]]

  :global-vars {*warn-on-reflection* true}

  ;; The ^replace is done to disable the tiered compilation for accurate benchmarks
  ;; See https://github.com/technomancy/leiningen/wiki/Faster
  ;; Use the following to enable JMX profiling with visualvm
  :jvm-opts ^:replace ["-server"
                       ;; Uncomment this to enable assertions. Turn off during performance tests.
                       "-ea"
                       "-Dcom.sun.management.jmxremote"
                       "-Dcom.sun.management.jmxremote.ssl=false"
                       "-Dcom.sun.management.jmxremote.authenticate=false"
                       "-Dcom.sun.management.jmxremote.port=1098"]

  ; :jvm-opts ^:replace ["-server"]

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]

         :source-paths ["src" "dev" "test"]}})


