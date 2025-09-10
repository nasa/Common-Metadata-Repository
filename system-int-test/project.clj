(defproject nasa-cmr/cmr-system-int-test "0.1.0-SNAPSHOT"
  :description "This project provides end to end integration testing for CMR components."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/system-int-test"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :parent-project {:path "../project.clj"
                   :inherit [:managed-dependencies]}
  :dependencies [[cheshire]
                 [clj-http "2.3.0"] ;; behind other projects
                 [clj-time]
                 [clj-xml-validation "1.0.2"]
                 [com.google.code.findbugs/jsr305 "3.0.2"]
                 [commons-codec/commons-codec]
                 [commons-io]
                 [crouton "0.1.2"]
                 [inflections]
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
                 [org.clojure/clojure]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.clojure/tools.reader]
                 [org.jsoup/jsoup "1.14.2"]
                 [potemkin]
                 [prismatic/schema "1.1.9"]
                 [ring/ring-codec "1.3.0"]
                 [ring/ring-core "1.14.2"]
                 [ring/ring-jetty-adapter "1.14.2"]
                 [org.eclipse.jetty/jetty-http "12.0.21"]
                 [org.eclipse.jetty/jetty-util "12.0.21"]
                 [org.eclipse.jetty/jetty-io "12.0.21"]]
  :plugins [[lein-parent "0.3.9"]
            [lein-shell "0.5.0"]]
  :jvm-opts ^:replace ["-server"
                       "-XX:-OmitStackTraceInFastThrow"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.4.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"
                                           :properties-file "resources/security/dependencycheck.properties"}}
             :dev {:dependencies [[org.clojars.gjahad/debug-repl]
                                  [org.clojure/tools.namespace]
                                  [org.clojure/tools.nrepl]
                                  [pjstadig/humane-test-output]
                                  [ring/ring-jetty-adapter "1.14.2"]]
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
             :kaocha {:dependencies [[ring/ring-jetty-adapter "1.14.2"]
                                     [lambdaisland/kaocha "1.0.732"]
                                     [lambdaisland/kaocha-cloverage "1.0.75"]
                                     [lambdaisland/kaocha-junit-xml "0.0.76"]]}}
  :aliases {;; Kaocha test aliases
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
