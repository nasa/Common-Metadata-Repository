(defproject nasa-cmr/cmr-metadata-db-app "0.1.0-SNAPSHOT"
  :description "The metadata db is a micro-service that provides
               support for persisting metadata concepts."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/metadata-db-app"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [nasa-cmr/cmr-oracle-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-message-queue-lib "0.1.0-SNAPSHOT"]
                 [compojure "1.5.1"]
                 [ring/ring-core "1.5.0"]
                 [ring/ring-json "0.4.0"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [drift "1.5.3"]
                 [inflections "0.9.14"]
                 [org.quartz-scheduler/quartz "2.2.2"]]

  :plugins [[test2junit "1.2.1"]
            [drift "1.5.3"]
            [lein-exec "0.3.2"]]

  :repl-options {:init-ns user}
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {
    :dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                         [org.clojars.gjahad/debug-repl "0.3.3"]
                         [proto-repl "0.3.1"]
                         [pjstadig/humane-test-output "0.8.1"]
                         [clj-http "2.0.0"]
                         [nasa-cmr/cmr-mock-echo-app "0.1.0-SNAPSHOT"]]
          :jvm-opts ^:replace ["-server"]
          :source-paths ["src" "dev" "test" "int_test"]
          :injections [(require 'pjstadig.humane-test-output)
                       (pjstadig.humane-test-output/activate!)]}
    :integration-test {:test-paths ["int_test"]
                       :dependencies [[clj-http "2.0.0"]]}
    :uberjar {:main cmr.metadata-db.runner
              :aot :all}
    ;; This profile is used for linting and static analysis. To run for this
    ;; project, use `lein lint` from inside the project directory. To run for
    ;; all projects at the same time, use the same command but from the top-
    ;; level directory.
    :lint {
      :source-paths ^:replace ["src"]
      :test-paths ^:replace []
      :plugins [[jonase/eastwood "0.2.3"]
                [lein-ancient "0.6.10"]
                [lein-bikeshed "0.4.1"]
                [lein-kibit "0.1.2"]
                [lein-shell "0.4.0"]
                [venantius/yagni "0.1.4"]]}}
  :test-paths ["test" "int_test"]
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
            "check-deps" ["with-profile" "lint" "ancient"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]})
