(defproject nasa-cmr/cmr-metadata-db-app "0.1.0-SNAPSHOT"
  :description "The metadata db is a micro-service that provides
               support for persisting metadata concepts."
  :url "***REMOVED***projects/CMR/repos/cmr-metadata-db-app/browse"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [nasa-cmr/cmr-oracle-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-system-trace-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
                 [compojure "1.1.9"]
                 [ring/ring-core "1.3.1" :exclusions [clj-time]]
                 [ring/ring-json "0.3.1"]
                 [org.clojure/tools.reader "0.8.8"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/java.jdbc "0.3.3"]
                 [sqlingvo "0.5.17"]
                 [drift "1.5.2"]
                 [inflections "0.9.9"]
                 [org.quartz-scheduler/quartz-oracle "2.1.7"]]

  :plugins [[lein-test-out "0.3.1"]
            [drift "1.5.2"]
            [lein-exec "0.3.2"]]

  :repl-options {:init-ns user}

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.5"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]
                        [cheshire "5.3.1"]
                        [clj-http "1.0.0"]]
         :source-paths ["src" "dev" "test" "int_test"]}
   :integration-test {:test-paths ["int_test"]
                      :dependencies [[clj-http "1.0.0"]]}
   :uberjar {:main cmr.metadata-db.runner
             :aot :all}}

  ;; Database migrations run by executing "lein migrate"
  :aliases {"create-user" ["exec" "-p" "./support/create_user.clj"]
            "drop-user" ["exec" "-p" "./support/drop_user.clj"]})



