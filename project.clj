(defproject nasa-cmr/cmr-ingest-app "0.1.0-SNAPSHOT"
  :description "Ingest is an external facing CMR service facilitating providers to create and  update their concepts in CMR. Internally it delegates concept persistence operations to metadata db and indexer micro services."

  :url "***REMOVED***projects/CMR/repos/cmr-ingest-app/browse"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-system-trace-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
                 [compojure "1.1.6"]
                 [ring/ring-core "1.2.2"]
                 [ring/ring-json "0.3.1"]
                 [clj-http "0.9.0"]
                 [org.clojure/tools.reader "0.8.4"]
                 [org.clojure/tools.cli "0.3.1"]]
  :plugins []
  :repl-options {:init-ns user}
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.5"]
                        [org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :source-paths ["src" "dev" "test"]}
   :uberjar {:main cmr.ingest.runner
             :aot :all}})


