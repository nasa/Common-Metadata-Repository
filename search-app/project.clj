;; Use the docs profile when generating the HTML documentation for the search application:
;; lein with-profile docs generate-docs
;;
;; All other lein tasks can use the default profile.
(defproject nasa-cmr/cmr-search-app "0.1.0-SNAPSHOT"
  :description "Provides a public search API for concepts in the CMR."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/search-app"
  ;; Need the maven repo for the echo-orbits-java jar that isn't available in public maven repos.
  :repositories [["releases" "http://devrepo1.dev.echo.nasa.gov/data/dist/projects/echo/mavenrepo/"]]
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [nasa-cmr/cmr-metadata-db-app "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-umm-spec-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-spatial-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-echo/echo-orbits-java "0.1.11"]
                 [ring/ring-core "1.4.0" :exclusions [clj-time]]
                 [ring/ring-json "0.4.0"]
                 [nasa-cmr/cmr-elastic-utils-lib "0.1.0-SNAPSHOT"]
                 [org.clojure/data.csv "0.1.3"]
                 [org.clojure/tools.nrepl "0.2.11"]
                 [net.sf.saxon/Saxon-HE "9.6.0-7"]
                 [com.github.fge/json-schema-validator "2.2.6"]]
  :plugins [[test2junit "1.2.1"]
            [lein-exec "0.3.4"]]
  :repl-options {:init-ns user
                 :timeout 120000}
  :jvm-opts []
  :resource-paths ["resources" "gems"]
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.5"]
                        [org.clojure/tools.namespace "0.2.11"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]
                        [criterium "0.4.3"]
                        [pjstadig/humane-test-output "0.7.0"]
                        ;; Must be listed here as metadata db depends on it.
                        [drift "1.5.3"]]
         :source-paths ["src" "dev" "test"]
         :injections [(require 'pjstadig.humane-test-output)
                      (pjstadig.humane-test-output/activate!)]}

   ;; This profile specifically here for generating documentation. It's faster than using the regular
   ;; profile. An agent pool is being started when using the default profile which causes the wait of
   ;; 60 seconds before allowing the JVM to shutdown since no call to shutdown-agents is made.
   ;; Generate docs with: lein with-profile docs generate-docs
   :docs {}

   :uberjar {:main cmr.search.runner
             :aot :all}}

  :aliases {"generate-docs"
            ["exec" "-ep"
             (pr-str '(do
                       (use 'cmr.common-app.api-docs)
                       (use 'clojure.java.io)
                       (let [json-target (file "resources/public/site/JSONQueryLanguage.json")
                             aql-target (file "resources/public/site/IIMSAQLQueryLanguage.xsd")]
                         (println "Copying JSON Query Language Schema to" (str json-target))
                         (make-parents json-target)
                         (copy (file "resources/schema/JSONQueryLanguage.json")
                               json-target)
                         (println "Copying AQL Schema to" (str aql-target))
                         (copy (file "resources/schema/IIMSAQLQueryLanguage.xsd")
                               aql-target))
                       (generate
                         "CMR Search"
                         "api_docs.md"
                         "resources/public/site/search_api_docs.html")))]
            ;; Prints out documentation on configuration environment variables.
            "env-config-docs" ["exec" "-ep" "(do (use 'cmr.common.config) (print-all-configs-docs) (shutdown-agents))"]
            ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]})
