(defproject nasa-cmr/cmr-message-queue-lib "0.1.0-SNAPSHOT"
  :description "Library containing code to handle message queue interactions within the CMR."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/message-queue-lib"
  :dependencies [
    [com.amazonaws/aws-java-sdk "1.10.60"]
    [com.novemberain/langohr "3.4.0"]
    [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
    [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
    [org.clojure/clojure "1.8.0"]]

  :plugins [
    [lein-shell "0.4.0"]
    [test2junit "1.2.1"]]
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :aot [cmr.message-queue.test.ExitException]
  :profiles {
    :dev {
      :dependencies [
        [org.clojure/tools.namespace "0.2.11"]
        [org.clojars.gjahad/debug-repl "0.3.3"]]
      :jvm-opts ^:replace ["-server"]
      :source-paths ["src" "dev" "test"]}
    :static {}
    ;; This profile is used for linting and static analysis. To run for this
    ;; project, use `lein lint` from inside the project directory. To run for
    ;; all projects at the same time, use the same command but from the top-
    ;; level directory.
    :lint {
      :source-paths ^:replace ["src"]
      :test-paths ^:replace []
      :plugins [
        [jonase/eastwood "0.2.3"]
        [lein-ancient "0.6.10"]
        [lein-bikeshed "0.4.1"]
        [lein-kibit "0.1.2"]
        [venantius/yagni "0.1.4"]]}}
  :aliases {
    ;; Alias to test2junit for consistency with lein-test-out
    "test-out" ["test2junit"]
    ;; Linting aliases
    "kibit"
      ["do"
        ["shell" "echo" "== Kibit =="]
        ["with-profile" "lint" "kibit"]]
    "eastwood"
      ["with-profile" "lint"
       "eastwood" "{:namespaces [:source-paths]}"]
    "bikeshed"
      ["with-profile" "lint"
       "bikeshed" "--max-line-length=100"]
    "yagni"
      ["with-profile" "lint" "yagni"]
    "check-deps"
      ["with-profile" "lint"
       "ancient" "all"]
    "lint"
      ["do"
        ["check"] ["kibit"] ["eastwood"]]
    ;; Placeholder for future docs and enabler of top-level alias
    "generate-static" ["with-profile" "static" "shell" "echo"]
    "start-sqs-sns"
      ["shell"
       "../dev-system/support/start-local-sqs-sns.sh"]
    "stop-sqs-sns"
      ["shell"
       "../dev-system/support/stop-local-sqs-sns.sh"]
    "restart-sqs-sns"
      ["do"
        ["stop-sqs-sns"]
        ["start-sqs-sns"]]})
