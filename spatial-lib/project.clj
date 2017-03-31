(defproject nasa-cmr/cmr-spatial-lib "0.1.0-SNAPSHOT"
  :description "A spatial library for the CMR."
  :url "***REMOVED***browse/spatial-lib"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]

                 ; Fast math library for atan2, acos, asin, etc
                 [net.jafama/jafama "2.1.0"]

                 ;; Matrix multiplication
                 [net.mikera/core.matrix "0.54.0"]

                 ;; Fast vectors
                 ;; I could not update this past 0.28.0 without a failure in code when trying to do
                 ;; a matrix multiply with two matrices. We should test updating this in the future
                 ;; when doing updates to see if it's been fixed or take a closer look at the code
                 ;; to make sure it's doing the right thing.
                 [net.mikera/vectorz-clj "0.28.0"]

                 ;; allows enable and disable when assertions run by jvm flags.
                 ;; Can skip assertions for better performance
                 [pjstadig/assertions "0.2.0"]

                 ;; Helps prevent auto boxing when performing math in Clojure
                 ;; Could not update to 0.1.5 due to "More than one matching method found: gt"
                 [primitive-math "0.1.4"]

                 ;; Added for combinations function
                 [org.clojure/math.combinatorics "0.1.3"]]


  :plugins [[test2junit "1.2.1"]]

  :global-vars {*warn-on-reflection* true}

  ;; The ^replace is done to disable the tiered compilation for accurate benchmarks
  ;; See https://github.com/technomancy/leiningen/wiki/Faster
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {
    :dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                         [org.clojars.gjahad/debug-repl "0.3.3"]
                         [criterium "0.4.4"]
                         [proto-repl "0.3.1"]
                         [pjstadig/humane-test-output "0.8.1"]]
          :injections [(require 'pjstadig.humane-test-output)
                       (pjstadig.humane-test-output/activate!)]
          :jvm-opts ^:replace ["-server"]
                              ;; Uncomment this to enable assertions. Turn off during performance tests.
                              ; "-ea"

                              ;; Use the following to enable JMX profiling with visualvm
                              ;  "-Dcom.sun.management.jmxremote"
                              ;  "-Dcom.sun.management.jmxremote.ssl=false"
                              ;  "-Dcom.sun.management.jmxremote.authenticate=false"
                              ;  "-Dcom.sun.management.jmxremote.port=1098"]
          :source-paths ["src" "dev" "test"]}
    ;; This profile is used for linting and static analisys. To run for this
    ;; project, use `lein lint` from inside the project directory. To run for
    ;; all projects at the same time, use the same command but from the top-
    ;; level directory.
    :lint {
      :source-paths ^:replace ["src"]
      :test-paths ^:replace []
      :plugins [[jonase/eastwood "0.2.3"]
                [lein-ancient "0.6.10"]
                [lein-bikeshed "0.4.1"]
                [lein-kibit "0.1.2"]
                [lein-shell "0.4.0"]
                [venantius/yagni "0.1.4"]]}}
  :aliases {;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]
            ;; Linting aliases
            "kibit" ["do" ["with-profile" "lint" "shell" "echo" "== Kibit =="]
                          ["with-profile" "lint" "kibit"]]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed"]
            "yagni" ["with-profile" "lint" "yagni"]
            "check-deps" ["with-profile" "lint" "ancient"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]})
