(defproject nasa-cmr/cmr-search-app "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [compojure "1.1.6"]
                 [ring/ring-core "1.2.1"]
                 [ring/ring-json "0.2.0"]
                 [org.clojure/tools.reader "0.8.3"]
                 [org.clojure/tools.cli "0.3.1"]
                 [clojurewerkz/elastisch "2.0.0-beta1"]
                 [clj-zipkin "0.1.2"]
                 [thrift-clj "0.2.1"]
                 [clj-scribe "0.3.1"]
                 [ch.qos.logback/logback-classic "1.0.13"]]
  :plugins []
  :main cmr.search.runner
  :repl-options {:init-ns user}
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.5"]
                        [org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :source-paths ["src" "dev" "test"]}})


