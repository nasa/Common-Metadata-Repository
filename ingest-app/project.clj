(defproject nasa-cmr/cmr-ingest-app "0.1.0-SNAPSHOT"
  :description "Ingest is an external facing CMR service facilitating providers to create and  update their concepts in CMR. Internally it delegates concept persistence operations to metadata db and indexer micro services."

  :url "***REMOVED***projects/CMR/repos/cmr/browse/ingest-app"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [nasa-cmr/cmr-system-trace-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-message-queue-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
                 [compojure "1.3.2"]
                 [ring/ring-core "1.3.2" :exclusions [clj-time]]
                 [ring/ring-json "0.3.1"]
                 [clj-http "1.0.1"]
                 [drift "1.5.2"]

                 ;; Database related
                 [org.quartz-scheduler/quartz-oracle "2.1.7"]
                 [nasa-cmr/cmr-oracle-lib "0.1.0-SNAPSHOT"]]

  :plugins [[lein-test-out "0.3.1"]
            [drift "1.5.2"]
            [lein-exec "0.3.4"]]

  :repl-options {:init-ns user}
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.5"]
                        [org.clojure/tools.namespace "0.2.10"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :source-paths ["src" "dev" "test"]}

   ;; This profile specifically here for generating documentation. It's faster than using the regular
   ;; profile. We're not sure why though. There must be something hooking into the regular profile
   ;; that's running at the end.
   ;; Generate docs with: lein with-profile docs generate-docs
   :docs {}

   :uberjar {:main cmr.ingest.runner
             :aot :all}}


  :aliases {"generate-docs" ["exec" "-ep" (pr-str '(do
                                                    (use 'cmr.common-app.api-docs)
                                                    (generate
                                                      "CMR Ingest"
                                                      "api_docs.md"
                                                      "resources/public/site/ingest_api_docs.html")))]
            ;; Database migrations run by executing "lein migrate"
            "create-user" ["exec" "-p" "./support/create_user.clj"]
            "drop-user" ["exec" "-p" "./support/drop_user.clj"]
            ;; Prints out documentation on configuration environment variables.
            "env-config-docs" ["exec" "-ep" "(do (use 'cmr.common.config) (print-all-configs-docs))"]})


