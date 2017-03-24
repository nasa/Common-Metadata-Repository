(defproject nasa-cmr/cmr-indexer-app "0.1.0-SNAPSHOT"
  :description "This is the indexer application for the CMR. It is responsible for indexing modified data into Elasticsearch."
  :url "***REMOVED***browse/indexer-app"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-umm-spec-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-message-queue-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
                 [compojure "1.5.1"]
                 [ring/ring-core "1.5.0"]
                 [ring/ring-json "0.4.0"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [nasa-cmr/cmr-elastic-utils-lib "0.1.0-SNAPSHOT"]]
  :plugins [[test2junit "1.2.1"]]
  :repl-options {:init-ns user}
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :jvm-opts ^:replace ["-server"]
         :source-paths ["src" "dev" "test"]}
   :uberjar {:main cmr.indexer.runner
             :aot :all}}
  :aliases {;; Prints out documentation on configuration environment variables.
            "env-config-docs" ["exec" "-ep" "(do (use 'cmr.common.config) (print-all-configs-docs) (shutdown-agents))"]
            ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]})
