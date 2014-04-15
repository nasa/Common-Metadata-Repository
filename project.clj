(defproject nasa-cmr/cmr-metadata-db-app "0.1.0-SNAPSHOT"
  :description "The metadata db is a micro-service that provides
               support for persisting metadata concepts."
  :url "***REMOVED***projects/CMR/repos/cmr-metadata-db-app/browse"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-system-trace-lib "0.1.0-SNAPSHOT"]
                 [compojure "1.1.6"]
                 [ring/ring-core "1.2.2"]
                 [ring/ring-json "0.3.0"]
                 [org.clojure/tools.reader "0.8.3"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/java.jdbc "0.3.3"]
                 [sqlingvo "0.5.16"]
                 [drift "1.5.2"]
                 [com.oracle/ojdbc6 "11.2.0.3"]
                 [com.mchange/c3p0 "0.9.2.1"]
                 [inflections "0.9.6"]
                 [byte-streams "0.1.10"]]
  ;; Need the maven repo for Oracle jars that aren't available in public maven repos.
  :repositories [["releases" "http://devrepo1.dev.echo.nasa.gov/data/dist/projects/echo/mavenrepo/"]]
  :plugins [[lein-test-out "0.3.1"]
            [drift "1.5.2"]
            [lein-exec "0.3.2"]]
  :repl-options {:init-ns user}
  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]
                        [cheshire "5.3.1"]
                        [clj-http "0.9.0"]
                        [drift "1.5.2"]
                        [org.clojure/test.check "0.5.7"]]
         :source-paths ["src" "dev" "test" "int_test"]}
   :integration-test {:test-paths ["int_test"]
                      :dependencies [[clj-http "0.9.1"]]}
   :uberjar {:main cmr.metadata-db.runner
             :aot :all}})



