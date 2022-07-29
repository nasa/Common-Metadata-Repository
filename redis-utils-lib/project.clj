(def redis-distribution
  "The version of redis to use and hash value of executable. See:
  https://github.com/antirez/redis-hashes/blob/master/README"
  {:version "3.2.10"
   :hash "411c604a716104f7f5a326abfad32de9cea10f15f987bec45cf86f315e9e63a0"})

(def redis-version "6")

(defproject nasa-cmr/cmr-redis-utils-lib "0.1.0-SNAPSHOT"
  :description "Library containing code to handling cacheing with the CMR."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/redis-utils-lib"
  :dependencies [[com.taoensso/carmine "3.0.1"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [org.clojure/clojure "1.10.0"]
                 [org.testcontainers/testcontainers "1.17.2"]]
  :plugins [[lein-exec "0.3.7"]
            [lein-shell "0.5.0"]
            [test2junit "1.3.3"]]
  :resource-paths ["resources"]
  :global-vars {*warn-on-reflection* true}
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.1.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"}}
             :dev {:exclusions [[org.clojure/tools.nrepl]]
                   :dependencies [[org.clojars.gjahad/debug-repl "0.3.3"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/tools.nrepl "0.2.13"]]
                   :jvm-opts ^:replace ["-server"]
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
                              [lein-kibit "0.1.6"]
                              [lein-shell "0.5.0"]]}
             ;; The following profile is overriden on the build server or in the user's
             ;; ~/.lein/profiles.clj file.
             :internal-repos {}
             :kaocha {:dependencies [[lambdaisland/kaocha "1.0.732"]
                                     [lambdaisland/kaocha-cloverage "1.0.75"]
                                     [lambdaisland/kaocha-junit-xml "0.0.76"]]}}
  :aliases {"bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "check-deps" ["with-profile" "lint" "ancient" ":all"]
            "check-sec" ["with-profile" "security" "dependency-check"]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "pull-docker-images" ["shell" "docker" "pull" ~(str "redis:" redis-version)]

            "install!" "install"

            ;; Placeholder for future docs and enabler of top-level alias
            "generate-static" ["with-profile" "static" "shell" "echo"]
            ;; Linting aliases
            "kibit" ["do"
                     ["with-profile" "lint" "shell" "echo" "== Kibit =="]
                     ["with-profile" "lint" "kibit"]]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]
            ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]

            ;; Kaocha test aliases
            ;; refer to tests.edn for test configuration
            "kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]
            "itest" ["kaocha" "--focus" ":integration"]
            "utest" ["kaocha" "--focus" ":unit"]
            "ci-test" ["kaocha" "--profile" ":ci"]
            "ci-itest" ["itest" "--profile" ":ci"]
            "ci-utest" ["utest" "--profile" ":ci"]})
