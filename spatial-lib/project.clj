(defproject nasa-cmr/cmr-spatial-lib "0.1.0-SNAPSHOT"
  :description "A spatial library for the CMR."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/spatial-lib"
  :exclusions [[org.clojure/clojure]]
  :dependencies [[nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [net.jafama/jafama "2.3.1"]
                 [net.mikera/core.matrix "0.54.0"]
                 [net.mikera/vectorz-clj "0.28.0"]
                 [org.clojure/clojure "1.10.0"]
                 [org.clojure/math.combinatorics "0.1.4"]
                 [pjstadig/assertions "0.2.0"]
                 [primitive-math "0.1.4"]]
  :plugins [[lein-shell "0.5.0"]
            [test2junit "1.3.3"]]
  :global-vars {*warn-on-reflection* true}
  ;; The ^replace is done to disable the tiered compilation for accurate benchmarks
  ;; See https://github.com/technomancy/leiningen/wiki/Faster
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.1.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"}}
             :dev {:exclusions [[org.clojure/tools.nrepl]]
                   :dependencies [[criterium "0.4.4"]
                                  [org.clojars.gjahad/debug-repl "0.3.3"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/tools.nrepl "0.2.13"]
                                  [pjstadig/humane-test-output "0.9.0"]
                                  [proto-repl "0.3.1"]]
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
             :static {}
             ;; This profile is used for linting and static analysis. To run for this
             ;; project, use `lein lint` from inside the project directory. To run for
             ;; all projects at the same time, use the same command but from the top-
             ;; level directory.
             :lint {:source-paths ^:replace ["src"]
                    :test-paths ^:replace []
                    :plugins [[jonase/eastwood "0.2.5"]
                              [lein-ancient "0.6.15"]
                              [lein-bikeshed "0.5.0"]
                              [lein-kibit "0.1.6"]]}
             ;; The following profile is overriden on the build server or in the user's
             ;; ~/.lein/profiles.clj file.
             :internal-repos {}
             :kaocha {:dependencies [[lambdaisland/kaocha "1.0.732"]
                                     ;; [lambdaisland/kaocha-cloverage "1.0.75"]
                                     [lambdaisland/kaocha-junit-xml "0.0.76"]]}}
  :aliases {;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]

            ;; Kaocha test aliases
            ;; refer to tests.edn for test configuration
            "kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]
            "itest" ["kaocha" "--focus" ":integration"]
            "utest" ["kaocha" "--focus" ":unit"]
            "ci-test" ["kaocha" "--profile" ":ci"]
            "ci-itest" ["itest" "--profile" ":ci"]
            "ci-utest" ["utest" "--profile" ":ci"]

            ;; Linting aliases
            "kibit" ["do"
                     ["with-profile" "lint" "shell" "echo" "== Kibit =="]
                     ["with-profile" "lint" "kibit"]]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "check-deps" ["with-profile" "lint" "ancient" ":all"]
            "check-sec" ["with-profile" "security" "dependency-check"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]
            ;; Placeholder for future docs and enabler of top-level alias
            "generate-static" ["with-profile" "static" "shell" "echo"]})
