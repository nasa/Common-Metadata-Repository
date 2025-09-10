(def aws-java-sdk-version
  "The java aws sdk version to use."
  "1.12.788") ;; latest as of 2025-09-05

(def aws-java-sdk2-version
  "The java aws sdk version to use."
  "2.33.4") ;; latest as of 2025-09-05

(defproject nasa-cmr/cmr-message-queue-lib "0.1.0-SNAPSHOT"
  :description "Library containing code to handle message queue interactions within the CMR."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/message-queue-lib"
  :parent-project {:path "../project.clj"
                   :inherit [:managed-dependencies]}
  :dependencies [[cheshire]
                 [clj-http "2.3.0"] ;;behind other cmr projects
                 [clj-time]
                 [io.netty/netty-handler "4.1.126.Final"]
                 [io.netty/netty-codec-http "4.1.126.Final"]
                 [com.amazonaws/aws-java-sdk-sns ~aws-java-sdk-version]
                 [com.amazonaws/aws-java-sdk-sqs ~aws-java-sdk-version]
                 [software.amazon.awssdk/regions ~aws-java-sdk2-version]
                 [software.amazon.awssdk/sns ~aws-java-sdk2-version
                  :exclusions [io.netty/netty-codec
                               io.netty/netty-codec-http
                               io.netty/netty-handler]]
                 [software.amazon.awssdk/sqs ~aws-java-sdk2-version
                  :exclusions [io.netty/netty-codec
                               io.netty/netty-codec-http
                               io.netty/netty-handler]]
                 [com.fasterxml.jackson.core/jackson-annotations "2.15.4"]
                 [commons-codec/commons-codec]
                 [commons-io]
                 [commons-logging "1.2"]
                 [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
                 [org.apache.httpcomponents/httpclient "4.5.13"]
                 [org.apache.httpcomponents/httpcore "4.4.10"]
                 [org.clojure/clojure]
                 [org.clojure/tools.reader]
                 ;; testcontainers needs a newer version of commons-compress, for now
                 ;; we will force it to use the latest version
                 [org.apache.commons/commons-compress]
                 [org.testcontainers/testcontainers]

                 [potemkin]
                 [ring/ring-core "1.14.2"]
                 [ring/ring-jetty-adapter "1.14.2"]]
  :plugins [[lein-parent "0.3.9"]
            [lein-shell "0.5.0"]]
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :aot [cmr.message-queue.test.ExitException]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.4.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"}}
             :dev {:dependencies [[org.clojure/tools.namespace]
                                  [org.clojars.gjahad/debug-repl]
                                  [org.clojure/tools.nrepl]
                                  [ring/ring-jetty-adapter "1.14.2"]]
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
