(def jruby-version
  "The version of JRuby to use. This is the same as used in the collection renderer
   java package to prevent classpath issues"
  "9.3.3.0")

(def dev-gem-install-path
  "The directory within this library where Ruby gems are installed for development time dependencies."
  "dev-gems")

(defproject nasa-cmr/cmr-orbits-lib "0.1.0-SNAPSHOT"
  :description "Contains Ruby code that allows performing orbit calculations for spatial search."
  :dependencies [[nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [org.clojure/clojure "1.10.0"]
                 [org.jruby/jruby-complete ~jruby-version]]
  :plugins [[lein-shell "0.5.0"]
            [test2junit "1.3.3"]]
  :resource-paths ["resources"]
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.1.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"
                                           :properties-file "resources/security/dependencycheck.properties"}}
             :dev {:exclusions [[org.clojure/tools.nrepl]]
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/tools.nrepl "0.2.13"]
                                  [pjstadig/humane-test-output "0.9.0"]
                                  [proto-repl "0.3.1"]]
                   :jvm-opts ^:replace ["-server"]
                   :source-paths ["src" "dev" "test"]
                   :resource-paths ["resources" "test_resources" ~dev-gem-install-path]
                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]}
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
                                     [lambdaisland/kaocha-cloverage "1.0.75"]
                                     [lambdaisland/kaocha-junit-xml "0.0.76"]]}}
  :aliases {"install-gems" ["shell"
                            "support/install_gems.sh"
                            ~jruby-version
                            ~dev-gem-install-path]
            "clean-gems" ["shell" "rm" "-rf" ~dev-gem-install-path]
            "install" ["do" "clean," "deps," "clean-gems," "install-gems," "install"]
            "install!" "install"
            ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]

            ;; Kaocha test aliases
            ;; refer to tests.edn for test configuration
            "kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]
            "itest" ["kaocha" "--focus" ":integration"]
            "utest" ["kaocha" "--focus" ":unit"]
            "ci-test" ["do"
                       ["clean-gems"]
                       ["install-gems"]
                       ["kaocha" "--profile" ":ci"]]
            "ci-itest" ["do"
                        ["clean-gems"]
                        ["install-gems"]
                        ["itest" "--profile" ":ci"]]
            "ci-utest" ["do"
                        ["clean-gems"]
                        ["install-gems"]
                        ["utest" "--profile" ":ci"]]

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
