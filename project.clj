(def projects
  "A map of the other development projects to their versions"
  {:cmr-ingest-app "0.1.0-SNAPSHOT"
   :cmr-search-app "0.1.0-SNAPSHOT"
   :cmr-indexer-app "0.1.0-SNAPSHOT"
   :cmr-bootstrap-app "0.1.0-SNAPSHOT"
   :cmr-metadata-db-app "0.1.0-SNAPSHOT"
   :cmr-index-set-app "0.1.0-SNAPSHOT"
   :cmr-common-lib "0.1.1-SNAPSHOT"
   :cmr-acl-lib "0.1.0-SNAPSHOT"
   :cmr-transmit-lib "0.1.0-SNAPSHOT"
   :cmr-spatial-lib "0.1.0-SNAPSHOT"
   :cmr-vdd-spatial-viz "0.1.0-SNAPSHOT"
   :cmr-es-spatial-plugin "0.1.0-SNAPSHOT"
   :cmr-umm-lib "0.1.0-SNAPSHOT"
   :cmr-system-trace-lib "0.1.0-SNAPSHOT"
   :cmr-elastic-utils-lib "0.1.0-SNAPSHOT"
   :cmr-system-int-test "0.1.0-SNAPSHOT"
   :cmr-oracle-lib "0.1.0-SNAPSHOT"
   :cmr-mock-echo-app "0.1.0-SNAPSHOT"})

(def project-dependencies
  "A list of other projects as maven dependencies"
  (doall (map (fn [[project-name version]]
                (let [maven-name (symbol "nasa-cmr" (name project-name))]
                  [maven-name version]))
              projects)))

(def create-checkouts-commands
  (vec
    (apply concat ["do"
                   "shell" "mkdir" "checkouts,"]
           (map (fn [project-name]
                  ["shell" "ln" "-s" (str "../../" (name project-name)) "checkouts/,"])
                (keys projects)))))

;; The version number here is for the sprint number. It will be incremented each sprint. The second
;; number is for which delivery of the version was given to ECHO for use.
(defproject nasa-cmr/cmr-dev-system "0.11.1"
  :description "Dev System combines together the separate microservices of the CMR into a single
               application to make it simpler to develop."
  :url "***REMOVED***projects/CMR/repos/cmr-dev-system/browse"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  ;; Due to a stack overflow issue in the latest version of leiningen we can only list the top level
  ;; libraries in the dependencies. Sub dependencies that are also under another project can't be
  ;; included
  ; :dependencies ~(concat '[[org.clojure/clojure "1.6.0"]]
  ;                        project-dependencies)
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [nasa-cmr/cmr-ingest-app "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-bootstrap-app "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-index-set-app "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-mock-echo-app "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-es-spatial-plugin "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-system-int-test "0.1.0-SNAPSHOT"]]


  :plugins [[lein-shell "0.4.0"]]
  :repl-options {:init-ns user}


  :jvm-opts ["-XX:PermSize=256m" "-XX:MaxPermSize=256m"]

  ;; Uncomment this for performance testing or profiling
  ; :jvm-opts ^:replace ["-server"
  ;                      "-XX:PermSize=256m"
  ;                      "-XX:MaxPermSize=256m"
  ;                      ;; Use the following to enable JMX profiling with visualvm
  ;                      ; "-Dcom.sun.management.jmxremote"
  ;                      ; "-Dcom.sun.management.jmxremote.ssl=false"
  ;                      ; "-Dcom.sun.management.jmxremote.authenticate=false"
  ;                      ; "-Dcom.sun.management.jmxremote.port=1098"
  ;                      ]


  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]
                        [pjstadig/humane-test-output "0.6.0"]
                        [criterium "0.4.3"]
                        ;; Must be listed here as metadata db depends on it.
                        [drift "1.5.2"]]
         :source-paths ["src" "dev" "test"]
         :injections [(require 'pjstadig.humane-test-output)
                      (pjstadig.humane-test-output/activate!)]}
   :uberjar {:main cmr.dev-system.runner
             ;; See http://stephen.genoprime.com/2013/11/14/uberjar-with-titan-dependency.html
             :uberjar-merge-with {#"org\.apache\.lucene\.codecs\.*" [slurp str spit]}
             :aot :all}}
  :aliases {;; Creates the checkouts directory to the local projects
            "create-checkouts" ~create-checkouts-commands})

