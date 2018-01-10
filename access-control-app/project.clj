(defproject nasa-cmr/cmr-access-control-app "0.1.0-SNAPSHOT"
  :description "Implements the CMR access control application."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/access-control-app"
  :exclusions [
    [cheshire]
    [clj-time]
    [com.fasterxml.jackson.core/jackson-core]
    [commons-codec/commons-codec]
    [commons-io]
    [org.clojure/tools.reader]
    [ring/ring-codec]]
  :dependencies [
    [cheshire "5.8.0"]
    [clj-time "0.14.2"]
    [com.fasterxml.jackson.core/jackson-core "2.9.3"]
    [commons-codec/commons-codec "1.11"]
    [commons-io "2.6"]
    [compojure "1.6.0"]
    [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
    [nasa-cmr/cmr-elastic-utils-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-message-queue-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-metadata-db-app "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-umm-spec-lib "0.1.0-SNAPSHOT"]
    [org.clojure/clojure "1.8.0"]
    [org.clojure/tools.reader "1.1.1"]
    [ring/ring-codec "1.1.0"]
    [ring/ring-core "1.6.3"]
    [ring/ring-json "0.4.0"]]
  :plugins [
    [lein-exec "0.3.7"]
    [lein-shell "0.5.0"]
    [test2junit "1.3.3"]]
  :repl-options {:init-ns user}
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {
    :dev {
      :exclusions [
        [org.clojure/tools.nrepl]]
      :dependencies [
        [nasa-cmr/cmr-mock-echo-app "0.1.0-SNAPSHOT"]
        [org.clojure/tools.namespace "0.2.11"]
        [org.clojure/tools.nrepl "0.2.13"]
        [pjstadig/humane-test-output "0.8.3"]
        [proto-repl "0.3.1"]
        [ring-mock "0.1.5"]]
      :jvm-opts ^:replace ["-server"]
      :source-paths ["src" "dev" "test" "int_test"]
      :test-paths ["test" "int_test"]
      :injections [(require 'pjstadig.humane-test-output)
                   (pjstadig.humane-test-output/activate!)]}
    ;; This profile specifically here for generating documentation. It's faster
    ;; than using the regular profile. An agent pool is being started when
    ;; using the default profile which causes the wait of 60 seconds before
    ;; allowing the JVM to shutdown since no call to shutdown-agents is made.
    ;; Generate docs with: lein generate-static (the alias makes use of the
    ;; static profile).
    :static {}
    :uberjar {
      :main cmr.access-control.runner
      :aot :all}
    ;; This profile is used for linting and static analysis. To run for this
    ;; project, use `lein lint` from inside the project directory. To run for
    ;; all projects at the same time, use the same command but from the top-
    ;; level directory.
    :lint {
      :source-paths ^:replace ["src"]
      :test-paths ^:replace []
      :plugins [
        [jonase/eastwood "0.2.5"]
        [lein-ancient "0.6.15"]
        [lein-bikeshed "0.5.0"]
        [lein-kibit "0.1.6"]
        [venantius/yagni "0.1.4"]]}}
  :test-paths ["test" "int_test"]
  :aliases {"generate-static" ["with-profile" "static"
                               "run" "-m" "cmr.access-control.site.static" "all"]
            ;; Prints out documentation on configuration environment variables.
            "env-config-docs" ["exec" "-ep" "(do (use 'cmr.common.config) (print-all-configs-docs))"]
            ;; Alias to test2junit for consistency with lein-test-out.
            "test-out" ["test2junit"]
            ;; Linting aliases
            "kibit" ["do" ["with-profile" "lint" "shell" "echo" "== Kibit =="]
                          ["with-profile" "lint" "kibit"]]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "yagni" ["with-profile" "lint" "yagni"]
            "check-deps" ["with-profile" "lint" "ancient" ":all"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]})
