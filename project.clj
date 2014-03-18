(defproject nasa-cmr/cmr-ingest-app "0.1.0-SNAPSHOT"
  :description "Ingest is an external facing CMR service facilitating providers
               to create / update their concepts in CMR. Internally it delegates 
               concept persistence operations to metadata db and indexer micro services."
  :url "***REMOVED***projects/CMR/repos/cmr-ingest-app/browse"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [compojure "1.1.6"]
                 [ring/ring-core "1.2.1"]
                 [ring/ring-json "0.2.0"]
                 [org.clojure/tools.reader "0.8.3"]
                 [clj-http "0.9.0"]
                 [cheshire "5.3.1"]
                 [org.clojure/tools.cli "0.3.1"]]
  :plugins []
  :main cmr.ingest.runner
  :repl-options {:init-ns user}
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.5"]
                        [org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :source-paths ["src" "dev" "test"]}})


