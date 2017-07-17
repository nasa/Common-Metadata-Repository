(defproject nasa-cmr/cmr-ingest-app "0.1.0-SNAPSHOT"
  :description "Ingest is an external facing CMR service facilitating providers to create and  update their concepts in CMR. Internally it delegates concept persistence operations to metadata db and indexer micro services."

  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/ingest-app"

  :dependencies [
    [clj-http "2.0.0"]
    [compojure "1.5.1"]
    [drift "1.5.3"]
    [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-message-queue-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-oracle-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-umm-spec-lib "0.1.0-SNAPSHOT"]
    [org.clojure/clojure "1.8.0"]
    [org.clojure/tools.nrepl "0.2.12"]
    [org.quartz-scheduler/quartz "2.2.2"]
    [potemkin "0.4.3"]
    [ring/ring-core "1.5.0"]
    [ring/ring-json "0.4.0"]]
  :plugins [
    [drift "1.5.3"]
    [lein-exec "0.3.4"]
    [test2junit "1.2.1"]]
  :repl-options {:init-ns user}
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {
    :dev {
      :dependencies [
        [org.clojars.gjahad/debug-repl "0.3.3"]
        [org.clojure/tools.namespace "0.2.11"]
        [ring-mock "0.1.5"]]
      :jvm-opts ^:replace ["-server"]
      :source-paths ["src" "dev" "test"]}
    ;; This profile specifically here for generating documentation. It's faster than using the regular
    ;; profile. An agent pool is being started when using the default profile which causes the wait of
    ;; 60 seconds before allowing the JVM to shutdown since no call to shutdown-agents is made.
    ;; Generate docs with: lein generate-static
    :static {}
    :uberjar {
      :main cmr.ingest.runner
      :aot :all}
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
        [lein-shell "0.4.0"]
        [venantius/yagni "0.1.4"]]}}
  :aliases {"generate-static" ["with-profile" "static"
                               "run" "-m" "cmr.ingest.site.static" "all"]
            ;; Database migrations run by executing "lein migrate"
            "create-user" ["exec" "-p" "./support/create_user.clj"]
            "drop-user" ["exec" "-p" "./support/drop_user.clj"]
            ;; Prints out documentation on configuration environment variables.
            "env-config-docs" ["exec" "-ep" "(do (use 'cmr.common.config) (print-all-configs-docs) (shutdown-agents))"]
            ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]
            ;; Linting aliases
            "kibit" ["do" ["with-profile" "lint" "shell" "echo" "== Kibit =="]
                          ["with-profile" "lint" "kibit"
                           ;; XXX the following are placed here to implicitly
                           ;; avoid cmr.ingest.validation, and in particular,
                           ;; the `additional-attribute-validation` ns due to
                           ;; it's use of namespace qualified keywords. This
                           ;; is not yet supported by kibit:
                           ;;     https://github.com/jonase/kibit/issues/14
                           "src/cmr/ingest/api"
                           "src/cmr/ingest/data"
                           "src/cmr/ingest/services"]]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "yagni" ["with-profile" "lint" "yagni"]
            "check-deps" ["with-profile" "lint" "ancient" "all"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]})

