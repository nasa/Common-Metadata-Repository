(defproject nasa-cmr/cmr-metadata-db-app "0.1.0-SNAPSHOT"
  :description "The metadata db is a micro-service that provides
               support for persisting metadata concepts."
  :url "***REMOVED***projects/CMR/repos/cmr-metadata-db-app/browse"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [compojure "1.1.6"]
                 [ring/ring-core "1.2.1"]
                 [ring/ring-json "0.2.0"]
                 [org.clojure/tools.reader "0.8.3"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/java.jdbc "0.3.3"]
                 [drift "1.5.2"]
                 [com.oracle/ojdbc6 "11.2.0.3"]
                 [slingshot "0.10.3"]]
  :repositories [["releases" "http://devrepo1.dev.echo.nasa.gov/data/dist/projects/echo/mavenrepo/"]]
  :plugins [[drift "1.5.2"]
            [lein-exec "0.3.2"]]
  :main cmr.metadata-db.runner
  :repl-options {:init-ns user}
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.5"]
                        [org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]
                        [cheshire "5.3.1"]
                        [clj-http "0.9.0"]
                        [drift "1.5.2"]]
         :source-paths ["src" "dev" "test" "int_test"]}})



