(defproject nasa-cmr/cmr-oracle-lib "0.1.0-SNAPSHOT"
  :description "Contains utilities for connecting to and manipulating data in Oracle."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/oracle-lib"
  :parent-project {:path "../project.clj"
                   :inherit [:managed-dependencies]}
  ;; Dynamically include extra repositories in the project definition if configured.
  :dependencies [[com.oracle.database.jdbc/ojdbc8 "19.14.0.0"]
                 [com.oracle.database.ha/ons "19.14.0.0"]
                 [com.oracle.database.jdbc/ucp "19.14.0.0"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [org.clojure/clojure]
                 [org.clojure/java.jdbc "0.4.2"]
                 [sqlingvo "0.7.15"]]
  :plugins [[lein-parent "0.3.9"]
            [lein-shell "0.5.0"]]
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.4.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"}}
             :dev {:dependencies [[org.clojars.gjahad/debug-repl]
                                  [org.clojure/tools.namespace]
                                  [org.clojure/tools.nrepl]]
                   :jvm-opts ^:replace ["-server"]
                   :source-paths ["src" "dev" "test"]}
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
                                     [lambdaisland/kaocha-junit-xml "0.0.76"]]}}
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
            ;; Eastwood needs special handling with libs that include oracle
            ;; drivers in the deps, in particulear:
            ;;   java.lang.ClassNotFoundException: oracle.dms.console.DMSConsole
            "eastwood" ["with-profile" "lint" "eastwood"
                        "{:namespaces [:source-paths] :exclude-namespaces [cmr.oracle.connection]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "check-deps" ["with-profile" "lint" "ancient" ":all"]
            "check-sec" ["with-profile" "security" "dependency-check"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]
            ;; Placeholder for future docs and enabler of top-level alias
            "generate-static" ["with-profile" "static" "shell" "echo"]})
