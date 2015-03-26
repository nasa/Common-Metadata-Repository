(defproject nasa-cmr/cmr-metadata-db-app "0.1.0-SNAPSHOT"
  :description "The metadata db is a micro-service that provides
               support for persisting metadata concepts."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/metadata-db-app"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [nasa-cmr/cmr-oracle-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-system-trace-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
                 [compojure "1.3.2"]
                 [ring/ring-core "1.3.2" :exclusions [clj-time]]
                 [ring/ring-json "0.3.1"]
                 [org.clojure/tools.reader "0.8.15"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/java.jdbc "0.3.6"]
                 [sqlingvo "0.7.8"]
                 [drift "1.5.2"]
                 [inflections "0.9.13"]
                 [org.quartz-scheduler/quartz-oracle "2.1.7"]]

  :plugins [[lein-test-out "0.3.1"]
            [drift "1.5.2"]
            [lein-exec "0.3.2"]]

  :repl-options {:init-ns user}

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.10"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]
                        [pjstadig/humane-test-output "0.7.0"]
                        [clj-http "1.0.1"]
                        [nasa-cmr/cmr-mock-echo-app "0.1.0-SNAPSHOT"]]
         :source-paths ["src" "dev" "test" "int_test"]
         :injections [(require 'pjstadig.humane-test-output)
                      (pjstadig.humane-test-output/activate!)]}
   :integration-test {:test-paths ["int_test"]
                      :dependencies [[clj-http "1.0.1"]]}
   :uberjar {:main cmr.metadata-db.runner
             :aot :all}}

  ;; Database migrations run by executing "lein migrate"
  :aliases {"create-user" ["exec" "-p" "./support/create_user.clj"]
            "drop-user" ["exec" "-p" "./support/drop_user.clj"]
            ;; Prints out documentation on configuration environment variables.
            "env-config-docs" ["exec" "-ep" "(do (use 'cmr.common.config) (print-all-configs-docs))"]})



