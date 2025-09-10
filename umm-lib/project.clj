(defproject nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"
  :description "The UMM (Unified Metadata Model) Library is responsible for defining the common domain
               model for Metadata Concepts in the CMR along with code to parse and generate the
               various dialects of each concept."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/umm-lib"
  :parent-project {:path "../project.clj"
                   :inherit [:managed-dependencies]}
  :dependencies [[nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-spatial-lib "0.1.0-SNAPSHOT"]
                 [org.clojure/clojure]
                 [org.clojure/tools.reader]]
  :plugins [[lein-parent "0.3.9"]
            [lein-shell "0.5.0"]]
  ;; The ^replace is done to disable the tiered compilation for accurate benchmarks
  ;; See https://github.com/technomancy/leiningen/wiki/Faster
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.4.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"}}
             :dev {:dependencies [[criterium "0.4.4"]
                                  [org.clojars.gjahad/debug-repl]
                                  [org.clojure/tools.namespace]
                                  [org.clojure/tools.nrepl]
                                  [pjstadig/humane-test-output]
                                  [proto-repl]]
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
                                (pjstadig.humane-test-output/activate!)]}
             :static {}
             ;; This profile is used for linting and static analysis. To run for this
             ;; project, use `lein lint` from inside the project directory. To run for
             ;; all projects at the same time, use the same command but from the top-
             ;; level directory.
             :lint {:source-paths ^:replace ["src"]
                    :test-paths ^:replace []
                    :plugins [[jonase/eastwood "1.4.2"]
                              [lein-ancient "0.7.0"]
                              [lein-bikeshed "0.5.0"]
                              [lein-kibit "0.1.6"]]}
             ;; The following profile is overriden on the build server or in the user's
             ;; ~/.lein/profiles.clj file.
             :internal-repos {}
             :kaocha {:dependencies [[lambdaisland/kaocha "1.0.732"]
                                     [lambdaisland/kaocha-cloverage "1.0.75"]
                                     [lambdaisland/kaocha-junit-xml "0.0.76"]
                                     ;; ring is needed or this fails in sys int group3
                                     [ring/ring-jetty-adapter "1.14.2"]]}}
  :aliases {;; Kaocha test aliases
            ;; refer to tests.edn for test configuration
            "kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]
            "itest" ["kaocha" "--focus" ":integration"]
            "ptest" ["kaocha" "--focus" ":perf"]
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
