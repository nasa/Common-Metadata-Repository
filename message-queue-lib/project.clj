(def aws-java-sdk-version
  "The java aws sdk version to use."
  "1.12.663")

(defproject nasa-cmr/cmr-message-queue-lib "0.1.0-SNAPSHOT"
  :description "Library containing code to handle message queue interactions within the CMR."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/message-queue-lib"
  :exclusions [[cheshire]
               [clj-http]
               [clj-time]
               [commons-codec/commons-codec]
               [commons-io]
               [commons-logging]
               [org.apache.httpcomponents/httpclient]
               [org.apache.httpcomponents/httpcore]
               [org.clojure/tools.reader]
               [potemkin]]
  :dependencies [[cheshire "5.10.0"]
                 [clj-http "2.3.0"]
                 [clj-time "0.15.1"]
                 [com.amazonaws/aws-java-sdk-sns ~aws-java-sdk-version
                  :exclusions [com.fasterxml.jackson.core/jackson-databind]]
                 [com.amazonaws/aws-java-sdk-sqs ~aws-java-sdk-version]
                 [com.fasterxml.jackson.core/jackson-core "2.13.2"]
                 [com.fasterxml.jackson.core/jackson-annotations "2.13.2"]
                 [com.fasterxml.jackson.core/jackson-databind "2.13.2.1"]
                 [com.fasterxml.jackson.dataformat/jackson-dataformat-cbor "2.13.2"]
                 [commons-codec/commons-codec "1.11"]
                 [commons-io "2.6"]
                 [commons-logging "1.2"]
                 [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
                 [org.apache.httpcomponents/httpclient "4.5.13"]
                 [org.apache.httpcomponents/httpcore "4.4.10"]
                 [org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.reader "1.3.2"]
                 [potemkin "0.4.5"]]
  :plugins [[lein-shell "0.5.0"]]
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :aot [cmr.message-queue.test.ExitException]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.4.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"}}
             :dev {:exclusions [[org.clojure/tools.nrepl]]
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [org.clojars.gjahad/debug-repl "0.3.3"]
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
            "kibit"
            ["do"
             ["shell" "echo" "== Kibit =="]
             ["with-profile" "lint" "kibit"]]
            "eastwood"
            ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed"
            ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "check-deps"
            ["with-profile" "lint" "ancient" ":all"]
            "check-sec"
            ["with-profile" "security" "dependency-check"]
            "lint"
            ["do"
             ["check"] ["kibit"] ["eastwood"]]
            ;; Placeholder for future docs and enabler of top-level alias
            "generate-static" ["with-profile" "static" "shell" "echo"]
            ;; Run a local copy of SQS/SNS
            "start-sqs-sns"
            ["shell" "cmr" "start" "local" "sqs-sns"]
            "stop-sqs-sns"
            ["shell" "cmr" "stop" "local" "sqs-sns"]
            "restart-sqs-sns"
            ["do"
             ["stop-sqs-sns"]
             ["start-sqs-sns"]]})
