(def projects
  "A map of the other direct dependency projects to their versions"
  {:cmr-common-lib "0.1.1-SNAPSHOT"
   :cmr-acl-lib "0.1.0-SNAPSHOT"
   :cmr-transmit-lib "0.1.0-SNAPSHOT"
   :cmr-elastic-utils-lib "0.1.0-SNAPSHOT"
   :cmr-message-queue-lib "0.1.0-SNAPSHOT"
   :cmr-common-app-lib "0.1.0-SNAPSHOT"

   ;; Only used for the integration testing utilities
   :cmr-metadata-db-app "0.1.0-SNAPSHOT"
   :cmr-mock-echo-app "0.1.0-SNAPSHOT"})

(defn project-map->dependencies
  "Creates a list of dependencies from a project map."
  [project-map]
  (doall (map (fn [[project-name version]]
                (let [maven-name (symbol "nasa-cmr" (name project-name))]
                  [maven-name version]))
              project-map)))

(def create-checkouts-commands
  (vec
    (apply concat ["do"
                   "shell" "mkdir" "checkouts,"]
           (map (fn [project-name]
                  ["shell" "ln" "-s" (str "../../" (subs (name project-name) 4)) "checkouts/,"])
                (concat (keys projects) (keys dev-projects))))))

(defproject nasa-cmr/cmr-access-control-app "0.1.0-SNAPSHOT"
  :description "Implements the CMR access control application."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/access-control-app"
  :dependencies ~(concat '[[org.clojure/clojure "1.7.0"]
                           [compojure "1.4.0"]
                           [ring/ring-core "1.4.0" :exclusions [clj-time]]
                           [ring/ring-json "0.4.0"]]
                           ;; Temporarily fix broken build. The dev-projects depenedencies should
                           ;; not be overall depenedencies
                         (project-map->dependencies (merge projects projects)))
  :plugins [[lein-test-out "0.3.1"]
            [lein-shell "0.4.0"]
            [lein-exec "0.3.4"]]
  :repl-options {:init-ns user}
  :profiles
  {:dev {:dependencies ~(concat '[[org.clojure/tools.namespace "0.2.11"]
                                  [pjstadig/humane-test-output "0.7.0"]]
                                (project-map->dependencies dev-projects))
         :source-paths ["src" "dev" "test" "int_test"]
         :test-paths ["test" "int_test"]
         :injections [(require 'pjstadig.humane-test-output)
                      (pjstadig.humane-test-output/activate!)]}

   ;; This profile specifically here for generating documentation. It's faster than using the regular
   ;; profile. An agent pool is being started when using the default profile which causes the wait of
   ;; 60 seconds before allowing the JVM to shutdown since no call to shutdown-agents is made.
   ;; Generate docs with: lein with-profile docs generate-docs
   :docs {}

   :uberjar {:main cmr.access-control.runner
             :aot :all}}
  :aliases {"generate-docs" ["exec" "-ep" (pr-str '(do
                                                    (use 'cmr.common-app.api-docs)
                                                    (generate
                                                      "CMR Access Control"
                                                      "api_docs.md"
                                                      "resources/public/site/access_control_api_docs.html")))]
            ;; Prints out documentation on configuration environment variables.
            "env-config-docs" ["exec" "-ep" "(do (use 'cmr.common.config) (print-all-configs-docs))"]
            ;; Creates the checkouts directory to the local projects
            "create-checkouts" ~create-checkouts-commands})
