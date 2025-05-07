(def elastic-version "7.17.14")

(defproject nasa-cmr/cmr-elastic-utils-lib "0.1.0-SNAPSHOT"
  :description "A library containing utilities for dealing with Elasticsearch."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/elastic-utils-lib"
  :dependencies [[cheshire "5.12.0"]
                 [clj-http "3.11.0"]
                 [clojurewerkz/elastisch "5.0.0-beta1"]
                 [commons-codec/commons-codec "1.11"]
                 [commons-io "2.6"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
                 [org.apache.logging.log4j/log4j-api "2.15.0"]
                 [org.clojure/clojure "1.11.2"]
                 [org.elasticsearch/elasticsearch ~elastic-version]
                 [org.apache.commons/commons-compress "1.26.0"]
                 [org.testcontainers/testcontainers "1.19.7"]
                 [org.yaml/snakeyaml "1.31"]
                 [potemkin "0.4.5"]]
  :plugins [[lein-shell "0.5.0"]]
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :resource-paths ["resources"]
  :global-vars {*warn-on-reflection* true}
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.4.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"}}
             :dev {:dependencies [[org.clojars.gjahad/debug-repl "0.3.3"]
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
                    :global-vars {*warn-on-reflection* false}
                    :plugins [[jonase/eastwood "1.4.2"]
                              [lein-ancient "0.7.0"]
                              [lein-bikeshed "0.5.2"]
                              [lein-kibit "0.1.8"]]}
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
            "kondo" ["do"
                     ["shell" "clj-kondo" "--lint" "src" "--lint" "test" "--parallel"]]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "check-deps" ["with-profile" "lint" "ancient" ":all"]
            "check-sec" ["with-profile" "security" "dependency-check"]
            "lint" ["do" ["check"] ["kibit"] ["kondo"]]
            ;; Get kibana and elasticsearch images
            "pull-docker-images" ["do"
                                  ["shell" "docker" "pull" ~(str "docker.elastic.co/elasticsearch/elasticsearch:" elastic-version)]
                                  ["shell" "docker" "pull" ~(str "docker.elastic.co/kibana/kibana:" elastic-version)]]
            "install!" "install"
            ;; Placeholder for future docs and enabler of top-level alias
            "generate-static" ["with-profile" "static" "shell" "echo" "no generate-static action needed for elastic-search"]})
