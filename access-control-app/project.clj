(def cmr-deps
  '[[nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
    [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-elastic-utils-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-message-queue-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-umm-spec-lib "0.1.0-SNAPSHOT"]])

(def dev-cmr-deps
  '[[nasa-cmr/cmr-metadata-db-app "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-mock-echo-app "0.1.0-SNAPSHOT"]])

(defproject nasa-cmr/cmr-access-control-app "0.1.0-SNAPSHOT"
  :description "Implements the CMR access control application."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/access-control-app"
  :dependencies ~(concat '[[org.clojure/clojure "1.7.0"]
                           [compojure "1.4.0"]
                           [ring/ring-core "1.4.0" :exclusions [clj-time]]
                           [ring/ring-json "0.4.0"]]
                   cmr-deps)
  :plugins [[test2junit "1.2.1"]
            [lein-shell "0.4.0"]
            [lein-exec "0.3.4"]]
  :repl-options {:init-ns user}
  :profiles
  {:dev {:dependencies ~(into '[[org.clojure/tools.namespace "0.2.11"]
                                [pjstadig/humane-test-output "0.7.0"]
                                [proto-repl "0.2.0"]]
                          dev-cmr-deps)
         :source-paths ["src" "dev" "test" "int_test"]
         :test-paths ["test" "int_test"]
         :injections [(require 'pjstadig.humane-test-output)
                      (pjstadig.humane-test-output/activate!)]}

   ;; This profile specifically here for generating documentation. It's faster than using the regular
   ;; profile. An agent pool is being started when using the default profile which causes the wait of
   ;; 60 seconds before allowing the JVM to shutdown since no call to shutdown-agents is made.
   ;; Generate docs with: lein with-profile docs generate-docs
   :docs {}

   :uberjar {:main cmr.access-control.runner
             :aot :all}}
  :test-paths ["test" "int_test"]
  :aliases {"generate-docs" ["exec" "-ep" (pr-str '(do
                                                     (use 'cmr.common-app.api-docs)
                                                     (generate
                                                       "CMR Access Control"
                                                       "api_docs.md"
                                                       "resources/public/site/access_control_api_docs.html")))]
            ;; Prints out documentation on configuration environment variables.
            "env-config-docs" ["exec" "-ep" "(do (use 'cmr.common.config) (print-all-configs-docs))"]
            ;; Creates the checkouts directory to the local projects
            "create-checkouts" ~(reduce into ["do" "shell" "mkdir" "-p" "checkouts,"]
                                  (for [[group-artifact _] (concat cmr-deps dev-cmr-deps)
                                        :let [project-dir (.replace (name group-artifact) "cmr-" "")]]
                                    ["shell" "ln" "-s" (str "../../" project-dir) "checkouts/,"]))
            ;; Alias to test2junit for consistency with lein-test-out.
            "test-out" ["test2junit"]})
