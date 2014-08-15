(defproject nasa-cmr/cmr-indexer-app "0.1.0-SNAPSHOT"
  :description "This is the indexer application for the CMR. It is responsible for indexing modified data into Elasticsearch."
  :url "***REMOVED***projects/CMR/repos/cmr-indexer-app/browse"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-system-trace-lib "0.1.0-SNAPSHOT"]
                 [compojure "1.1.6"]
                 [ring/ring-core "1.2.2"]
                 [ring/ring-json "0.3.1"]
                 [org.clojure/tools.reader "0.8.4"]
                 [org.clojure/tools.cli "0.3.1"]
                 [nasa-cmr/cmr-elastic-utils-lib "0.1.0-SNAPSHOT"]]
  :plugins [[lein-test-out "0.3.1"]]
  :repl-options {:init-ns user}
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.5"]
                        [org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :source-paths ["src" "dev" "test"]}
   :uberjar {:main cmr.indexer.runner
             :aot :all}})


