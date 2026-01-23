;; This top level project file is used to provide a single place to run lein tasks against all of
;; the CMR libraries and applications. It makes use of lein-modules to accomplish the orchestration.
;; See https://github.com/jcrossley3/lein-modules for details.
;;
;; Examples:
;; 'lein modules clean' will run lein clean in every project subdirectory.
;; 'lein modules install' will run lein install in every project subdirectory. Lein-modules will
;; make sure to build the libraries and applications in the correct order based on the individual
;; project dependencies.
;; 'lein with-profile uberjar modules uberjar' will build uberjars for all of the CMR applications
;; listed in the uberjar profile.
;; 'CMR_ELASTIC_PORT=9206 lein modules do clean, install, test-out' will set the elastic port to use
;; as an environment variable, run clean in all project subdirectories, install in all project
;; subdirectories, and then test-out in all project subdirectories.
(defproject nasa-cmr/cmr "0.1.0-SNAPSHOT"
  :description "Top level project to support all CMR libraries and applications."
  :plugins [[lein-modules "0.3.11"]
            [lein-parent "0.3.9"]
            [lein-shell "0.5.0"]]
   :managed-dependencies [[at.yawk.lz4/lz4-java "1.10.1"]
                          [cheshire "5.12.0"] ;; latest is 6.1.0
                          [clj-http "3.11.0"] ;; latest is 3.13.1
                          [clj-time "0.15.1"] ;; latest is 0.15.2
                          [org.clojure/clojure "1.11.2"] ;; lattest is 1.11.4 or 1.12.2
                          [org.apache.commons/commons-compress "1.28.0"] ;; see testcontainers
                          [org.testcontainers/testcontainers "2.0.2" ;; latest
                           :exclusions [[org.apache.commons/commons-compress]]]]
  :profiles {:uberjar {:modules {:dirs ["access-control-app"
                                        "bootstrap-app"
                                        "indexer-app"
                                        "ingest-app"
                                        "metadata-db-app"
                                        "search-app"
                                        "virtual-product-app"
                                        "es-spatial-plugin"
                                        "dev-system"]}}}
  :modules {:dirs [;; Libraries and Resources
                   "acl-lib"
                   "common-app-lib"
                   "common-lib"
                   "elastic-utils-lib"
                   "es-spatial-plugin"
                   "message-queue-lib"
                   "oracle-lib"
                   "orbits-lib"
                   "redis-utils-lib"
                   "schema-validation-lib"
                   "schemas"
                   "site-templates"
                   "spatial-lib"
                   "system-int-test"
                   "transmit-lib"
                   "umm-lib"
                   "umm-spec-lib"

                   ;; Services
                   "access-control-app"
                   "bootstrap-app"
                   "indexer-app"
                   "ingest-app"
                   "metadata-db-app"
                   "mock-echo-app"
                   "search-app"
                   "virtual-product-app"

                   ;; Related apps
                   "dev-system"
                   "search-relevancy-test"]}
  :aliases {"kibit"
            ["modules" "kibit"]

            "eastwood"
            ["modules" "eastwood"]

            "lint"
            ["modules" "lint"]

            ;; The reason for this is to export the current list of modules for use in scripts. For
            ;; instance, a script may wish to run all the unit tests for sub projects and said
            ;; script does not want to hard code what modules those are.
            "dump" ["modules"]

            "check-deps"
            ["modules" "check-deps"]

            "check-sec"
            ["modules" "check-sec"]

            "deps-tree-conflicts"
            ["modules" "deps" ":tree"]

            "generate-static"
            ["modules" "generate-static"]

            ;; Run a local copy of SQS/SNS
            "start-sqs-sns"
            ["shell" "cmr" "start" "local" "sqs-sns"]

            "stop-sqs-sns"
            ["shell" "cmr" "stop" "local" "sqs-sns"]

            "restart-sqs-sns"
            ["do"
             ["stop-sqs-sns"]
             ["start-sqs-sns"]]

            ;; Dev
            "clean-all" ["modules" "do" "clean"]

            "repl"
            ["shell"
             "echo" "You need to be in the `dev-system` directory for that."]

            "test"
            ["modules" "test-out"]

            ;; Do not use modules to run unit tests, instead use a script where multiple threads can
            ;; be used to process the projects faster.
            "ci-utest"
            ["shell" ./bin/unit_test_script/run_unit_tests.py]

            ;; Install tasks using up-stream .jar repos
            "install-no-clean!"
            ["modules" "do" "clean," "install,"]

            "install!"
            ["modules" "do" "clean," "install," "clean"]

            "install-with-content-no-clean!"
            ["modules" "do" "clean," "generate-static," "install,"]

            "install-with-content!"
            ["modules" "do" "clean," "generate-static," "install," "clean"]

            ;; Install tasks using nexus .jar repos proxy
            "internal-install-no-clean!"
            ["modules" "with-profile" "+internal-repos" "do" "clean," "install,"]

            "internal-install!"
            ["modules" "with-profile" "+internal-repos" "do" "clean," "install," "clean"]

            "internal-install-with-content-no-clean!"
            ["modules" "with-profile" "+internal-repos" "do" "clean," "generate-static," "install,"]

            "internal-install-with-content!"
            ["modules" "with-profile" "+internal-repos" "do" "clean," "generate-static," "install," "clean"]})
