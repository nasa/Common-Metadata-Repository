(defproject nasa-cmr/cmr-access-control-app "0.1.0-SNAPSHOT"
  :description "Implements the CMR access control application."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/access-control-app"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
                 [compojure "1.4.0"]
                 [ring/ring-core "1.4.0" :exclusions [clj-time]]
                 [ring/ring-json "0.4.0"]]
  :plugins [[lein-test-out "0.3.1"]]
  :repl-options {:init-ns user}
  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                        [pjstadig/humane-test-output "0.7.0"]
                        [nasa-cmr/cmr-metadata-db-app "0.1.0-SNAPSHOT"]
                        [nasa-cmr/cmr-mock-echo-app "0.1.0-SNAPSHOT"]]
         :source-paths ["src" "dev" "test" "int_test"]
         :injections [(require 'pjstadig.humane-test-output)
                      (pjstadig.humane-test-output/activate!)]}
   :uberjar {:main cmr.access-control.runner
             :aot :all}}
  :aliases {;; Prints out documentation on configuration environment variables.
            "env-config-docs" ["exec" "-ep" "(do (use 'cmr.common.config) (print-all-configs-docs))"]})


