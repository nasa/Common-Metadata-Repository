(defproject nasa-cmr/cmr-ingest-app "0.1.0-SNAPSHOT"
  :description "Ingest is an external facing CMR service facilitating providers to create and  update their concepts in CMR. Internally it delegates concept persistence operations to metadata db and indexer micro services."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/ingest-app"
  :dependencies [[camel-snake-kebab "0.4.2"]
                 [clj-http "2.3.0"]
                 [com.draines/postal "2.0.3"]
                 [jakarta.servlet/jakarta.servlet-api "4.0.4"] ;;5.x and 6.x did not work
                 [commons-fileupload "1.3.3"]
                 [commons-codec/commons-codec "1.11"]
                 [commons-io "2.18.0"]
                 [compojure "1.6.1"]
                 [io.github.jaybarra/drift "1.5.4.2-SNAPSHOT"]
                 [gov.nasa.earthdata/cmr-site-templates "0.1.1-SNAPSHOT"]
                 [instaparse "1.4.10"]
                 [inflections "0.13.0"]
                 [markdown-clj "1.10.5"]
                 [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-message-queue-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-metadata-db-app "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-oracle-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-redis-utils-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-schemas "0.0.1-SNAPSHOT"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-umm-spec-lib "0.1.0-SNAPSHOT"]
                 [net.sf.saxon/Saxon-HE "9.9.0-2"]
                 [org.apache.httpcomponents/httpclient "4.5.13"]
                 [org.apache.httpcomponents/httpcore "4.4.10"]
                 [org.clojure/clojure "1.11.2"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.nrepl "0.2.13"]
                 [org.quartz-scheduler/quartz "2.3.2"]
                 [org.slf4j/slf4j-api "1.7.30"]
                 [org.yaml/snakeyaml "1.31"]
                 [potemkin "0.4.5"]
                 [org.eclipse.jetty/jetty-http "12.0.21"]
                 [org.eclipse.jetty/jetty-util "12.0.21"]
                 [ring/ring-core "1.14.2"]
                 [ring/ring-jetty-adapter "1.14.2"]
                 [ring/ring-codec "1.3.0"]
                 [ring/ring-json "0.5.1"]]
  :plugins [[io.github.jaybarra/drift "1.5.4.2-SNAPSHOT"]
            [lein-exec "0.3.7"]]
  :repl-options {:init-ns user}
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.4.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"}}
             :dev {:dependencies [[org.clojars.gjahad/debug-repl "0.3.3"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [ring/ring-jetty-adapter "1.14.2"]
                                  [ring-mock "0.1.5"]]
                   :jvm-opts ^:replace ["-server"]
                   :source-paths ["src" "dev" "test"]}
             ;; This profile specifically here for generating documentation. It's faster than using the regular
             ;; profile. An agent pool is being started when using the default profile which causes the wait of
             ;; 60 seconds before allowing the JVM to shutdown since no call to shutdown-agents is made.
             ;; Generate docs with: lein generate-static
             :static {:dependencies [[org.eclipse.jetty/jetty-http "12.0.21"]
                                     [org.eclipse.jetty/jetty-util "12.0.21"]
                                     ]}
             :uberjar {:main cmr.ingest.runner
                       :aot :all}
             ;; This profile is used for linting and static analysis. To run for this
             ;; project, use `lein lint` from inside the project directory. To run for
             ;; all projects at the same time, use the same command but from the top-
             ;; level directory.
             :lint {:source-paths ^:replace ["src"]
                    :test-paths ^:replace []
                    :plugins [[jonase/eastwood "1.4.2"]
                              [lein-ancient "0.7.0"]
                              [lein-bikeshed "0.5.0"]
                              [lein-kibit "0.1.6"]
                              [lein-shell "0.5.0"]]}
             ;; The following profile is overriden on the build server or in the user's
             ;; ~/.lein/profiles.clj file.
             :internal-repos {}
             :kaocha {:dependencies [[lambdaisland/kaocha "1.0.732"]
                                     [lambdaisland/kaocha-cloverage "1.0.75"]
                                     [lambdaisland/kaocha-junit-xml "0.0.76"]]}}
  :aliases {"generate-static" ["with-profile" "static"
                               "run" "-m" "cmr.ingest.site.static" "all"]
            ;; Database migrations run by executing "lein migrate"
            "migrate" ["migrate" "-c" "config.ingest-migrate-config/app-migrate-config-lein"]
            "create-user" ["exec" "-p" "./support/create_user.clj"]
            "drop-user" ["exec" "-p" "./support/drop_user.clj"]
            ;; Prints out documentation on configuration environment variables.
            "env-config-docs" ["exec" "-ep" "(do (use 'cmr.common.config) (print-all-configs-docs) (shutdown-agents))"]

            ;; Kaocha test aliases
            ;; refer to tests.edn for test configuration
            "kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]
            "itest" ["kaocha" "--focus" ":integration"]
            "utest" ["kaocha" "--focus" ":unit"]
            "ci-test" ["do"
                       ["generate-static"]
                       ["kaocha" "--profile" ":ci"]]
            "ci-itest" ["do"
                        ["generate-static"]
                        ["itest" "--profile" ":ci"]]
            "ci-utest" ["do"
                        ["generate-static"]
                        ["utest" "--profile" ":ci"]]

            ;; Linting aliases
            "kibit" ["do"
                     ["with-profile" "lint" "shell" "echo" "== Kibit =="]
                     ["with-profile" "lint" "kibit"
                      ;; XXX the following are placed here to implicitly
                      ;; avoid cmr.ingest.validation, and in particular,
                      ;; the `additional-attribute-validation` ns due to
                      ;; it's use of namespace qualified keywords. This
                      ;; is not yet supported by kibit:
                      ;;     https://github.com/jonase/kibit/issues/14
                      "src/cmr/ingest/api"
                      "src/cmr/ingest/data"
                      "src/cmr/ingest/services"]]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "check-deps" ["with-profile" "lint" "ancient" ":all"]
            "check-sec" ["with-profile" "security" "dependency-check"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]})
