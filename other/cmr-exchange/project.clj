;; This top level project file is used to provide a single place to run lein tasks against all of
;; the CMR exchange libraries and applications. It makes use of lein-modules to accomplish the orchestration.
;; See https://github.com/jcrossley3/lein-modules for details.
;;
;; Examples:
;; 'lein modules clean' will run lein clean in every project subdirectory.
;; 'lein modules install' will run lein install in every project subdirectory. Lein-modules will
;; make sure to build the libraries and applications in the correct order based on the individual
;; project dependencies.
(defproject nasa-cmr/cmr-exchange "0.1.0-SNAPSHOT"
  :description "Top level project to support all CMR Exchange libraries and applications."
  :plugins [[lein-modules "0.3.11"]
            [lein-shell "0.4.0"]]
  :profiles {:uberjar {:modules {:dirs ["cmr-exchange-common"
                                        "cmr-jar-plugin"
                                        "cmr-http-kit"
                                        "cmr-api-versioning"
                                        "cmr-authz"
                                        "cmr-exchange-query"
                                        "cmr-metadata-proxy"
                                        "cmr-ous-plugin"
                                        "cmr-sizing-plugin"
                                        "cmr-service-bridge"]}}}
  :aliases {"kibit"
            ["modules" "kibit"]
            "eastwood"
            ["modules" "eastwood"]
            "lint"
            ["modules" "lint"]
            "check-deps"
            ["modules" "check-deps"]
            "check-sec"
            ["modules" "check-sec"]
            "deps-tree-conflicts"
            ["modules" "deps" ":tree"]
            "generate-static"
            ["modules" "generate-static"]
            ;; Dev
            "clean-all" ["modules" "do" "clean"]
            "repl"
            ["shell"
             "echo" "You need to be in the `service-bridge` directory for that."]
            "test"
            ["modules" "test-out"]
            ;; Install tasks using up-stream .jar repos
            "install-no-clean!"
            ["modules" "do" "clean," "install,"]
            "install!"
            ["modules" "do" "clean," "install," "clean"]})
