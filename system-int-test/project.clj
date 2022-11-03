(defproject nasa-cmr/cmr-system-int-test "0.1.0-SNAPSHOT"
  :description "This project provides end to end integration testing for CMR components."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/system-int-test"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :exclusions [[cheshire]
               [clj-time]
               [com.fasterxml.jackson.core/jackson-core]
               [com.google.code.findbugs/jsr305]
               [commons-codec/commons-codec]
               [commons-io]
               [org.apache.httpcomponents/httpclient]
               [org.apache.httpcomponents/httpcore]
               [org.clojure/tools.logging]
               [org.clojure/tools.reader]
               [org.jsoup/jsoup]
               [potemkin]
               [ring/ring-codec]]
  :dependencies [[cheshire "5.10.0"]
                 [clj-http "2.3.0"]
                 [clj-time "0.15.1"]
                 [clj-xml-validation "1.0.2"]
                 [com.fasterxml.jackson.core/jackson-core "2.13.2"]
                 [com.fasterxml.jackson.core/jackson-databind "2.13.2.1"]
                 [com.fasterxml.jackson.dataformat/jackson-dataformat-cbor "2.13.2"]
                 [com.google.code.findbugs/jsr305 "3.0.2"]
                 [commons-codec/commons-codec "1.11"]
                 [commons-io "2.6"]
                 [crouton "0.1.2"]
                 [inflections "0.13.0"]
                 [nasa-cmr/cmr-access-control-app "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-bootstrap-app "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-elastic-utils-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-indexer-app "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-ingest-app "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-mock-echo-app "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-redis-utils-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-search-app "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-schemas "0.0.1-SNAPSHOT"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-umm-spec-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-umm-spec-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-virtual-product-app "0.1.0-SNAPSHOT"]
                 [org.apache.httpcomponents/httpclient "4.5.13"]
                 [org.apache.httpcomponents/httpcore "4.4.10"]
                 [org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.clojure/tools.reader "1.3.2"]
                 [org.jsoup/jsoup "1.14.2"]
                 [potemkin "0.4.5"]
                 [prismatic/schema "1.1.9"]
                 [ring/ring-codec "1.1.3"]
                 [ring/ring-core "1.9.2"]]
  :plugins [[lein-shell "0.5.0"]
            [test2junit "1.3.3"]]
  :jvm-opts ^:replace ["-server"
                       "-XX:-OmitStackTraceInFastThrow"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.1.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"
                                           :properties-file "resources/security/dependencycheck.properties"}}
             :dev {:exclusions [[org.clojure/tools.nrepl]]
                   :dependencies [[org.clojars.gjahad/debug-repl "0.3.3"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/tools.nrepl "0.2.13"]
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
  :aliases {;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]

            ;; Kaocha test aliases
            ;; refer to tests.edn for test configuration
            "kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]
            "itest" ["kaocha"]
            "utest" ["shell" "echo" "== No Unit Tests =="]
            "ci-test" ["kaocha" "--profile" ":ci"]
            "ci-itest" ["kaocha" "--profile" ":ci"]
            "ci-utest" ["utest"]

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
