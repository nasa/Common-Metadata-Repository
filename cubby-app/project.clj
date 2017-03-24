(defproject nasa-cmr/cmr-cubby-app "0.1.0-SNAPSHOT"
  :description "Provides a centralized caching service for the CMR. See README for details."
  :url "***REMOVED***browse/cubby-app"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
                 [compojure "1.5.1"]
                 [ring/ring-core "1.5.0"]
                 [ring/ring-json "0.4.0"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [nasa-cmr/cmr-elastic-utils-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]]
  :plugins [[test2junit "1.2.1"]
            [lein-exec "0.3.2"]]
  :repl-options {:init-ns user}
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]
                        [pjstadig/humane-test-output "0.8.1"]
                        [nasa-cmr/cmr-mock-echo-app "0.1.0-SNAPSHOT"]]
         :jvm-opts ^:replace ["-server"]
         :source-paths ["src" "dev" "test" "int_test"]
         :injections [(require 'pjstadig.humane-test-output)
                      (pjstadig.humane-test-output/activate!)]}
   :uberjar {:main cmr.cubby.runner
             :aot :all}}
  :test-paths ["int_test"]
  :aliases {;; Prints out documentation on configuration environment variables.
            "env-config-docs" ["exec" "-ep" "(do (use 'cmr.common.config) (print-all-configs-docs) (shutdown-agents))"]
            ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]})
