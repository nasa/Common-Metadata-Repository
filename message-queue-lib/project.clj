(def aws-java-sdk-version
  "The java aws sdk version to use."
  "1.12.663")

(def aws-java-sdk2-version
  "The java aws sdk version to use."
  "2.28.19")

(defproject nasa-cmr/cmr-message-queue-lib "0.1.0-SNAPSHOT"
  :description "Library containing code to handle message queue interactions within the CMR."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/message-queue-lib"
  :dependencies [[cheshire "5.12.0"]
                 [clj-http "2.3.0"]
                 [clj-time "0.15.1"]
                 [io.netty/netty-handler "4.1.118.Final"]
                 [com.amazonaws/aws-java-sdk-sns ~aws-java-sdk-version]
                 [com.amazonaws/aws-java-sdk-sqs ~aws-java-sdk-version]
                 [software.amazon.awssdk/regions ~aws-java-sdk2-version]
                 [software.amazon.awssdk/sns ~aws-java-sdk2-version]
                 [software.amazon.awssdk/sqs ~aws-java-sdk2-version]
                 [com.fasterxml.jackson.core/jackson-annotations "2.15.4"]
                 [commons-codec/commons-codec "1.11"]
                 [commons-io "2.18.0"]
                 [commons-logging "1.2"]
                 [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
                 [org.apache.httpcomponents/httpclient "4.5.13"]
                 [org.apache.httpcomponents/httpcore "4.4.10"]
                 [org.clojure/clojure "1.11.2"]
                 [org.clojure/tools.reader "1.3.2"]
                 [org.testcontainers/testcontainers "1.19.7"]
                 [potemkin "0.4.5"]]
  :plugins [[lein-shell "0.5.0"]]
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :aot [cmr.message-queue.test.ExitException]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.4.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"}}
             :dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
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
                    :plugins [[jonase/eastwood "1.4.2"]
                              [lein-ancient "0.7.0"]
                              [lein-bikeshed "0.5.2"]
                              [lein-kibit "0.1.8"]]}
             ;; The following profile is overriden on the build server or in the user's
             ;; ~/.lein/profiles.clj file.
             :internal-repos {}
             :kaocha {:dependencies [[lambdaisland/kaocha "1.0.732"]
                                     [lambdaisland/kaocha-cloverage "1.0.75"]
                                     [lambdaisland/kaocha-junit-xml "0.0.76"]
                                     ;; ring is needed or this fails in sys int group3
                                     [ring/ring-jetty-adapter "1.13.0"]]}}
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
            ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths] :exclude-linters [:reflection]}"]
            "bikeshed"
            ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "check-deps"
            ["with-profile" "lint" "ancient" ":all"]
            "ancient" ["with-profile" "lint" "ancient"]
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
