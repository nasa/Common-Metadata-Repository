(defproject nasa-cmr/cmr-index-set-app "0.1.0-SNAPSHOT"
  :description "index-set app is a microservice enabling CMR system create/maintain a logical set of indexes in Elasticsearch
               for indexing and searching for concepts."
  :url "***REMOVED***projects/CMR/repos/cmr-index-set-app/browse"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-system-trace-lib "0.1.0-SNAPSHOT"]
                 [compojure "1.1.6"]
                 [ring/ring-core "1.2.2"]
                 [ring/ring-json "0.3.0"]
                 [org.clojure/tools.reader "0.8.3"]
                 [clojurewerkz/elastisch "2.0.0-beta1" :exclusions [commons-codec]]
                 [org.clojure/tools.cli "0.3.1"]]
  :plugins []
  :repl-options {:init-ns user}
  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]
                        [cheshire "5.3.1"]
                        [clj-http "0.9.1"]
                        [org.clojure/test.check "0.5.7"]]
         :source-paths ["src" "dev" "test" "int_test"]}
   :integration-test {:test-paths ["int_test"]
                      :dependencies [[clj-http "0.9.1"]]}
   :uberjar {:main cmr.index-set.runner
             :aot :all}})


