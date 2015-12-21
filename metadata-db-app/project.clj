(defproject nasa-cmr/cmr-metadata-db-app "0.1.0-SNAPSHOT"
  :description "The metadata db is a micro-service that provides
               support for persisting metadata concepts."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/metadata-db-app"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [nasa-cmr/cmr-oracle-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-message-queue-lib "0.1.0-SNAPSHOT"]
                 [compojure "1.4.0"]
                 [ring/ring-core "1.4.0" :exclusions [clj-time]]
                 [ring/ring-json "0.4.0"]
                 [org.clojure/tools.nrepl "0.2.11"]
                 [drift "1.5.3"]
                 [inflections "0.9.14"]
                 [org.quartz-scheduler/quartz "2.2.2"]]

  :plugins [[lein-test-out "0.3.1"]
            [drift "1.5.3"]
            [lein-exec "0.3.2"]]

  :repl-options {:init-ns user}

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]
                        [pjstadig/humane-test-output "0.7.0"]
                        [clj-http "2.0.0"]
                        [nasa-cmr/cmr-mock-echo-app "0.1.0-SNAPSHOT"]]
         :source-paths ["src" "dev" "test" "int_test"]
         :injections [(require 'pjstadig.humane-test-output)
                      (pjstadig.humane-test-output/activate!)]}
   :integration-test {:test-paths ["int_test"]
                      :dependencies [[clj-http "2.0.0"]]}
   :uberjar {:main cmr.metadata-db.runner
             :aot :all}}

  ;; Database migrations run by executing "lein migrate"
  :aliases {"create-user" ["exec" "-p" "./support/create_user.clj"]
            "drop-user" ["exec" "-p" "./support/drop_user.clj"]
            ;; Prints out documentation on configuration environment variables.
            "env-config-docs" ["exec" "-ep" "(do (use 'cmr.common.config) (print-all-configs-docs) (shutdown-agents))"]})



