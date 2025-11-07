(defproject nasa-cmr/cmr-search-relevancy-test "0.1.0-SNAPSHOT"
  :description "Tests for measuring CMR search relevancy"
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/search-relevancy-test"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :parent-project {:path "../project.clj"
                   :inherit [:managed-dependencies]}
  :dependencies [[camel-snake-kebab "0.4.0"]
                 [cheshire]
                 [clj-http "2.3.0"]
                 [clj-time "0.15.1"]
                 [commons-codec/commons-codec "1.11"]
                 [commons-fileupload "1.6.0"]
                 [commons-io "2.18.0"]
                 [nasa-cmr/cmr-system-int-test "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
                 [org.apache.httpcomponents/httpclient "4.5.13"]
                 [org.apache.httpcomponents/httpcore "4.4.10"]
                 [org.clojure/clojure "1.11.2"]
                 [org.clojure/tools.reader "1.3.2"]
                 [potemkin "0.4.5"]]
  :plugins [[lein-parent "0.3.9"]
            [lein-shell "0.5.0"]]
  :main ^:skip-aot search-relevancy-test.runner
  :jvm-opts ^:replace ["-server"
                       "-XX:-OmitStackTraceInFastThrow"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.4.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"
                                           :properties-file "resources/security/dependencycheck.properties"}}
             :dev {:dependencies [[org.clojars.gjahad/debug-repl "0.3.3"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [pjstadig/humane-test-output "0.9.0"]]
                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]
                   :jvm-opts ^:replace ["-server"
                                        "-XX:-OmitStackTraceInFastThrow"]
                   :source-paths ["src" "dev"]}
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
