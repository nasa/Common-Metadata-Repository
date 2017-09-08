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
  :plugins [
    [lein-modules "0.3.11"]
    [lein-shell "0.4.0"]]

  :profiles {:uberjar {:modules {:dirs ["access-control-app"
                                        "cubby-app"
                                        "bootstrap-app"
                                        "index-set-app"
                                        "indexer-app"
                                        "ingest-app"
                                        "metadata-db-app"
                                        "search-app"
                                        "virtual-product-app"
                                        "es-spatial-plugin"]}}}
  :aliases {
    "kibit"
      ["modules" "kibit"]
    "eastwood"
      ["modules" "eastwood"]
    "lint"
      ["modules" "lint"]
    "check-deps"
      ["modules" "check-deps"]
    "generate-static"
      ["modules" "generate-static"]
    "install!"
      ["modules" "do"
       "clean," "install," "clean"]
    "install-with-content!"
      ["modules" "do"
       "clean," "install," "generate-static," "clean"]
    "start-sqs-sns"
      ["shell"
       "dev-system/support/start-local-sqs-sns.sh"]
    "stop-sqs-sns"
      ["shell"
       "dev-system/support/stop-local-sqs-sns.sh"]
    "restart-sqs-sns"
      ["do"
        ["stop-sqs-sns"]
        ["start-sqs-sns"]]
    "repl"
      ["shell"
       "echo" "You need to be in the `dev-system` directory for that."]})

