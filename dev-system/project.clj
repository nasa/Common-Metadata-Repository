(def projects
  "A map of the other development projects to their versions"
  {:cmr-access-control-app "0.1.0-SNAPSHOT"
   :cmr-acl-lib "0.1.0-SNAPSHOT"
   :cmr-bootstrap-app "0.1.0-SNAPSHOT"
   :cmr-common-app-lib "0.1.0-SNAPSHOT"
   :cmr-common-lib "0.1.1-SNAPSHOT"
   :cmr-schemas "0.0.1-SNAPSHOT"
   :cmr-elastic-utils-lib "0.1.0-SNAPSHOT"
   :cmr-indexer-app "0.1.0-SNAPSHOT"
   :cmr-ingest-app "0.1.0-SNAPSHOT"
   :cmr-message-queue-lib "0.1.0-SNAPSHOT"
   :cmr-metadata-db-app "0.1.0-SNAPSHOT"
   :cmr-mock-echo-app "0.1.0-SNAPSHOT"
   :cmr-oracle-lib "0.1.0-SNAPSHOT"
   :cmr-orbits-lib "0.1.0-SNAPSHOT"
   :cmr-redis-utils-lib "0.1.0-SNAPSHOT"
   :cmr-schema-validation-lib "0.1.0-SNAPSHOT"
   :cmr-search-app "0.1.0-SNAPSHOT"
   :cmr-search-relevancy-test "0.1.0-SNAPSHOT"
   :cmr-spatial-lib "0.1.0-SNAPSHOT"
   :cmr-system-int-test "0.1.0-SNAPSHOT"
   :cmr-transmit-lib "0.1.0-SNAPSHOT"
   :cmr-umm-lib "0.1.0-SNAPSHOT"
   :cmr-umm-spec-lib "0.1.0-SNAPSHOT"
   :cmr-virtual-product-app "0.1.0-SNAPSHOT"})

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
                  ["shell" "ln" "-s" (str "../../" (subs (name project-name) 4)) "checkouts/,"])
                (keys projects)))))

;; The version number here is for the sprint number. It will be incremented each sprint. The second
;; number is for which delivery of the version was given to ECHO for use.
(defproject nasa-cmr/cmr-dev-system "0.1.0-SNAPSHOT"
  :description "Dev System combines together the separate microservices of the CMR into a single
               application to make it simpler to develop."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/dev-system"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :exclusions [[commons-codec/commons-codec]
               [org.clojure/clojure]
               [ring/ring-codec]]
  :dependencies ~(concat '[[commons-codec/commons-codec "1.11"]
                           [org.clojure/clojure "1.10.0"]
                           [ring/ring-codec "1.1.1"]]
                         project-dependencies)
  :plugins [[lein-environ "1.1.0"]
            [lein-shell "0.5.0"]
            [test2junit "1.3.3"]]
  :resource-paths ["resources"]
  :repl-options {:init-ns user
                 :timeout 600000
                 :welcome (do
                            (println (slurp "resources/text/banner.txt"))
                            (println (slurp "resources/text/loading.txt")))}
  :jvm-opts ["-XX:-OmitStackTraceInFastThrow"
             "-Dclojure.compiler.direct-linking=true"
             "-Dorg.jruby.embed.localcontext.scope=singlethread"]
  ;; Uncomment to enable logging in jetty.
                                        ; "-Dorg.eclipse.jetty.util.log.class=org.eclipse.jetty.util.log.StrErrLog"
                                        ; "-Dorg.eclipse.jetty.LEVEL=INFO"
                                        ; "-Dorg.eclipse.jetty.websocket.LEVEL=INFO"]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.1.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"
                                           :properties-file "resources/security/dependencycheck.properties"}}
             :dev-dependencies {:exclusions [[org.clojure/tools.nrepl]]
                                :dependencies [[criterium "0.4.4"]
                                               [debugger "0.2.0"]
                                               [drift "1.5.3"]
                                               [org.clojars.gjahad/debug-repl "0.3.3"]
                                               [org.clojure/tools.namespace "0.2.11"]
                                               [org.clojure/tools.nrepl "0.2.13"]
                                               [pjstadig/humane-test-output "0.9.0"]
                                               [proto-repl "0.3.1"]
                                               [proto-repl-charts "0.3.2"]
                                               [proto-repl-sayid "0.1.3"]
                                               [ring-mock "0.1.5"]]
                                ;; XXX Note that profiling can be kept in a profile,
                                ;;     with no need to comment/uncomment.
                                ;; Use the following to enable JMX profiling with visualvm
                                        ;:jvm-opts ^:replace ["-server"
                                        ;                     "-Dcom.sun.management.jmxremote"
                                        ;                     "-Dcom.sun.management.jmxremote.ssl=false"
                                        ;                     "-Dcom.sun.management.jmxremote.authenticate=false"
                                        ;                     "-Dcom.sun.management.jmxremote.port=1098"]
                                :source-paths ["src" "dev" "test"]
                                :injections [(require 'pjstadig.humane-test-output)
                                             (pjstadig.humane-test-output/activate!)]}
             ;; This is to separate the dependencies from the dev-config specified in profiles.clj
             :dev [:dev-dependencies :dev-config]
             ;; The following run-* profiles are used in conjunction with other lein
             ;; profiles to set the default CMR run mode and may be used in the
             ;; following manner:
             ;;
             ;;   $ lein with-profile +run-external repl
             ;;
             ;; which will use dev and the other default profiles in addition to
             ;; run-external (or whichever run mode profile is given).
             :run-in-memory {:jvm-opts ["-Dcmr.runmode=in-memory"]}
             :run-external {:jvm-opts ["-Dcmr.runmode=external"]}
             :uberjar {:main cmr.dev-system.runner
                       ;; See http://stephen.genoprime.com/2013/11/14/uberjar-with-titan-dependency.html
                       :uberjar-merge-with {#"org\.apache\.lucene\.codecs\.*" [slurp str spit]}
                       :aot :all}
             :static {}
             ;; This profile is used for linting and static analysis. To run for this
             ;; project, use `lein lint` from inside the project directory. To run for
             ;; all projects at the same time, use the same command but from the top-
             ;; level directory.
             :lint {:source-paths ^:replace ["src"]
                    :test-paths ^:replace []
                    :plugins [[jonase/eastwood "0.2.5"]
                              [lein-ancient "0.6.15"]
                              [lein-bikeshed "0.5.0"]
                              [lein-kibit "0.1.6"]]}
             ;; The following profile is overriden on the build server or in the user's
             ;; ~/.lein/profiles.clj file.
             :internal-repos {}
             :kaocha {:dependencies [[lambdaisland/kaocha "1.0.732"]
                                     [lambdaisland/kaocha-cloverage "1.0.75"]
                                     [lambdaisland/kaocha-junit-xml "0.0.76"]]}}
  :aliases {
            ;; Creates the checkouts directory to the local projects
            "create-checkouts" ~create-checkouts-commands
            ;; Alias to test2junit for consistency with lein-test-out
            "test-out"
            ["test2junit"]

            ;; Kaocha test aliases
            ;; refer to tests.edn for test configuration
            "kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]
            "itest" ["kaocha" "--focus" ":integration"]
            "utest" ["kaocha" "--focus" ":unit"]
            "ci-test" ["kaocha" "--profile" ":ci"]
            "ci-itest" ["itest" "--profile" ":ci"]
            "ci-utest" ["utest" "--profile" ":ci"]

            ;; Install spatial plugin locally
            "install-spatial-plugin"
            ["shell" "cmr" "install" "local" "spatial_plugin"]
            ;; Install with local elasticsearch
            "install"
            ["do"
             "install-spatial-plugin,"
             "install"]
            "install!"
            ["install"]
            ;; Linting aliases
            "kibit"
            ["do"
             ["shell" "echo" "== Kibit =="]
             ["with-profile" "lint" "kibit"]]
            "eastwood"
            ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed"
            ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "check-deps"
            ["with-profile" "lint" "ancient" ":all"]
            "check-sec"
            ["with-profile" "security" "dependency-check"]
            "lint"
            ["do"
             ["check"] ["kibit"] ["eastwood"]]
            ;; Placeholder for future docs and enabler of top-level alias
            "generate-static"
            ["with-profile" "static"
             "shell" "echo"]
            ;; Run a local copy of SQS/SNS
            "start-sqs-sns"
            ["shell" "cmr" "start" "local" "sqs-sns"]
            "stop-sqs-sns"
            ["shell" "cmr" "stop" "local" "sqs-sns"]
            "restart-sqs-sns"
            ["do"
             ["stop-sqs-sns"]
             ["start-sqs-sns"]]})
