(def projects
  "A map of the other development projects to their versions"
  {:cmr-ingest-app "0.1.0-SNAPSHOT"
   :cmr-search-app "0.1.0-SNAPSHOT"
   :cmr-indexer-app "0.1.0-SNAPSHOT"
   :cmr-bootstrap-app "0.1.0-SNAPSHOT"
   :cmr-metadata-db-app "0.1.0-SNAPSHOT"
   :cmr-index-set-app "0.1.0-SNAPSHOT"
   :cmr-transformer-app "0.1.0-SNAPSHOT"
   :cmr-common-lib "0.1.1-SNAPSHOT"
   :cmr-transmit-lib "0.1.0-SNAPSHOT"
   :cmr-spatial-lib "0.1.0-SNAPSHOT"
   :cmr-vdd-spatial-viz "0.1.0-SNAPSHOT"
   :cmr-es-spatial-plugin "0.1.0-SNAPSHOT"
   :cmr-umm-lib "0.1.0-SNAPSHOT"
   :cmr-system-trace-lib "0.1.0-SNAPSHOT"
   :cmr-elastic-utils-lib "0.1.0-SNAPSHOT"
   :cmr-system-int-test "0.1.0-SNAPSHOT"
   :cmr-oracle-lib "0.1.0-SNAPSHOT"})

(def project-dependencies
  "A list of other projects as maven dependencies"
  (doall (map (fn [[project-name version]]
                (let [maven-name (symbol "nasa-cmr" (name project-name))]
                  [maven-name version]))
              projects)))

(def create-checkouts-commands
  (doall
    (apply concat ["do"
                   "shell" "mkdir" "checkouts,"]
           (map (fn [project-name]
                  ["shell" "ln" "-s" (str "../../" (name project-name)) "checkouts/,"])
                (keys projects)))))

(defproject nasa-cmr/cmr-dev-system "0.1.0-SNAPSHOT"
  :description "Dev System combines together the separate microservices of the CMR into a single
               application to make it simpler to develop."
  :url "***REMOVED***projects/CMR/repos/cmr-dev-system/browse"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies ~(concat '[[org.clojure/clojure "1.6.0"]
                           [nasa-cmr/cmr-elastic-utils-lib "0.1.0-SNAPSHOT"]]
                         project-dependencies)
  :plugins [[lein-shell "0.4.0"]]
  :repl-options {:init-ns user}
  :jvm-opts ["-XX:PermSize=256m" "-XX:MaxPermSize=256m"]
  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]
                        [pjstadig/humane-test-output "0.6.0"]]
         :source-paths ["src" "dev" "test"]
         :injections [(require 'pjstadig.humane-test-output)
                      (pjstadig.humane-test-output/activate!)]}
   :uberjar {:main cmr.dev-system.runner
             ;; See http://stephen.genoprime.com/2013/11/14/uberjar-with-titan-dependency.html
             :uberjar-merge-with {#"org\.apache\.lucene\.codecs\.*" [slurp str spit]}
             :aot :all}}
  :aliases {;; Creates the checkouts directory to the local projects
            "create-checkouts" ~create-checkouts-commands})

