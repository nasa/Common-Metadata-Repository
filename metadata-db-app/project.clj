(defproject nasa-cmr/cmr-metadata-db-app "0.1.0-SNAPSHOT"
  :description "The metadata db is a micro-service that provides
               support for persisting metadata concepts."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/metadata-db-app"
  :exclusions [
    [cheshire]
    [clj-time]
    [com.fasterxml.jackson.core/jackson-core]
    [com.fasterxml.jackson.core/jackson-databind]
    [org.apache.httpcomponents/httpcore]
    [org.clojure/tools.reader]
    [org.slf4j/slf4j-api]]
  :dependencies [
    [cheshire "5.8.0"]
    [clj-time "0.14.2"]
    [com.fasterxml.jackson.core/jackson-core "2.9.3"]
    [com.fasterxml.jackson.core/jackson-databind "2.9.3"]
    [compojure "1.6.0"]
    [drift "1.5.3"]
    [inflections "0.13.0"]
    [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
    [nasa-cmr/cmr-message-queue-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-oracle-lib "0.1.0-SNAPSHOT"]
    [org.apache.httpcomponents/httpcore "4.4.8"]
    [org.clojure/clojure "1.8.0"]
    [org.clojure/tools.nrepl "0.2.13"]
    [org.clojure/tools.reader "1.1.1"]
    [org.quartz-scheduler/quartz "2.3.0"]
    [org.slf4j/slf4j-api "1.7.10"]
    [ring/ring-core "1.6.3"]
    [ring/ring-json "0.4.0"]]
  :plugins [
    [drift "1.5.3"]
    [lein-exec "0.3.7"]
    [lein-shell "0.5.0"]
    [test2junit "1.3.3"]]
  :repl-options {:init-ns user}
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :test-paths ["test" "int-test"]
  :profiles {
    :dev {
      :dependencies [
        [clj-http "2.3.0"]
        [nasa-cmr/cmr-mock-echo-app "0.1.0-SNAPSHOT"]
        [org.clojars.gjahad/debug-repl "0.3.3"]
        [org.clojure/tools.namespace "0.2.11"]
        [pjstadig/humane-test-output "0.8.3"]
        [proto-repl "0.3.1"]]
      :jvm-opts ^:replace ["-server"]
      :source-paths ["src" "dev" "test" "int-test"]
      :injections [(require 'pjstadig.humane-test-output)
                   (pjstadig.humane-test-output/activate!)]}
    :integration-test {:test-paths ["int-test"]
                       :dependencies [[clj-http "2.3.0"]]}
    :uberjar {
      :main cmr.metadata-db.runner
      :aot :all}
    :static {}
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
        [venantius/yagni "0.1.4"]]}
    ;; The following profile is overriden on the build server or in the user's
    ;; ~/.lein/profiles.clj file.
    :internal-repos {}}
  ;; Database migrations run by executing "lein migrate"
  :aliases {"create-user" ["exec" "-p" "./support/create_user.clj"]
            "drop-user" ["exec" "-p" "./support/drop_user.clj"]
            ;; Prints out documentation on configuration environment variables.
            "env-config-docs" ["exec" "-ep" "(do (use 'cmr.common.config) (print-all-configs-docs) (shutdown-agents))"]
            ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]
            ;; Linting aliases
            "kibit" ["do" ["with-profile" "lint" "shell" "echo" "== Kibit =="]
                          ["with-profile" "lint" "kibit"]]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "yagni" ["with-profile" "lint" "yagni"]
            "check-deps" ["with-profile" "lint" "ancient" ":all"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]
            ;; Placeholder for future docs and enabler of top-level alias
            "generate-static" ["with-profile" "static" "shell" "echo"]})
