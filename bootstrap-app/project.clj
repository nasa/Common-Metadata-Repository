(defproject nasa-cmr/cmr-bootstrap-app "0.1.0-SNAPSHOT"
  :description "Bootstrap is a CMR application that can bootstrap the CMR with data from Catalog REST."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/bootstrap-app"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-http "1.0.1"]
                 [nasa-cmr/cmr-oracle-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-system-trace-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-metadata-db-app "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-indexer-app "0.1.0-SNAPSHOT"]
                 [compojure "1.3.2"]
                 [ring/ring-core "1.3.2" :exclusions [clj-time]]
                 [ring/ring-json "0.3.1"]
                 [org.clojure/tools.reader "0.8.15"]
                 [org.clojure/tools.cli "0.3.1"]
                 [sqlingvo "0.7.8"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]]
  :plugins [[lein-test-out "0.3.1"]
            [drift "1.5.2"]
            [lein-exec "0.3.2"]]
  :repl-options {:init-ns user}
  :jvm-opts ["-XX:PermSize=256m" "-XX:MaxPermSize=256m"]
  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.10"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :source-paths ["src" "dev" "test"]}
   :uberjar {:main cmr.bootstrap.runner
             :aot :all}}
  ;; Database migrations run by executing "lein migrate"
  :aliases {"create-user" ["exec" "-p" "./support/create_user.clj"]
            "drop-user" ["exec" "-p" "./support/drop_user.clj"]
            ;; Prints out documentation on configuration environment variables.
            "env-config-docs" ["exec" "-ep" "(do (use 'cmr.common.config) (print-all-configs-docs))"]})


