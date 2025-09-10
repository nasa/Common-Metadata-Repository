(defproject nasa-cmr/cmr-metadata-db-app "0.1.0-SNAPSHOT"
  :description "The metadata db is a micro-service that provides
               support for persisting metadata concepts."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/metadata-db-app"
  :parent-project {:path "../project.clj"
                   :inherit [:managed-dependencies]}
  :dependencies [[cheshire]
                 [clj-http]
                 [clj-time]
                 [commons-io] ;; used by migration
                 [compojure "1.6.1"
                  :exclusions [commons-fileupload]]
                 [io.github.jaybarra/drift "1.5.4.2-SNAPSHOT" :exclusions [clojure-tools]]
                 [inflections]
                 [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-message-queue-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-oracle-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-redis-utils-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-schemas "0.0.1-SNAPSHOT"]
                 [nasa-cmr/cmr-umm-spec-lib "0.1.0-SNAPSHOT"]
                 [org.clojure/clojure]
                 [org.clojure/tools.nrepl]
                 [org.clojure/tools.reader]
                 [org.quartz-scheduler/quartz "2.3.2"]
                 [org.slf4j/slf4j-api "1.7.30"]
                 [org.eclipse.jetty/jetty-http "12.0.21"]
                 [org.eclipse.jetty/jetty-util "12.0.21"]
                 [ring/ring-codec "1.3.0"]
                 [ring/ring-core "1.14.2"]
                 [ring/ring-jetty-adapter "1.14.2"] ;; used by migration
                 [ring/ring-json "0.5.1"]]
  :plugins [[io.github.jaybarra/drift "1.5.4.2-SNAPSHOT" :exclusions [clojure-tools]]
            [lein-exec "0.3.7"]
            [lein-parent "0.3.9"]
            [lein-shell "0.5.0"]]
  :repl-options {:init-ns user}
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :test-paths ["test" "int-test"]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.4.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"}}
             :dev {:dependencies [[nasa-cmr/cmr-mock-echo-app "0.1.0-SNAPSHOT"]
                                  [org.clojars.gjahad/debug-repl]
                                  [org.clojure/tools.namespace]
                                  [ring/ring-jetty-adapter "1.14.2"]
                                  [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                                  [nasa-cmr/cmr-message-queue-lib "0.1.0-SNAPSHOT"]
                                  [pjstadig/humane-test-output]
                                  [proto-repl]]
                   :jvm-opts ^:replace ["-server"]
                   :source-paths ["src" "dev" "test" "int-test"]
                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]}
             :uberjar {:main cmr.metadata-db.runner
                       :aot :all}
             :static {}
             ;; This profile is used for linting and static analysis. To run for this
             ;; project, use `lein lint` from inside the project directory. To run for
             ;; all projects at the same time, use the same command but from the top-
             ;; level directory.
             :lint {:source-paths ^:replace ["src"]
                    :test-paths ^:replace []
                    :plugins [[jonase/eastwood "1.4.2"]
                              [lein-ancient "0.7.0"]
                              [lein-bikeshed "0.5.0"]
                              [lein-kibit "0.1.6"]]}
             ;; The following profile is overriden on the build server or in the user's
             ;; ~/.lein/profiles.clj file.
             :internal-repos {}
             :kaocha {:dependencies [[lambdaisland/kaocha "1.0.732"]
                                     [lambdaisland/kaocha-cloverage "1.0.75"]
                                     [lambdaisland/kaocha-junit-xml "0.0.76"]]}}
  ;; Database migrations run by executing "lein migrate"
  :aliases {"create-user" ["exec" "-p" "./support/create_user.clj"]
            "drop-user" ["exec" "-p" "./support/drop_user.clj"]
            "migrate" ["migrate" "-c" "config.mdb-migrate-config/app-migrate-config-lein"]
            ;; Prints out documentation on configuration environment variables.
            "env-config-docs" ["exec" "-ep" "(do (use 'cmr.common.config) (print-all-configs-docs) (shutdown-agents))"]

            ;; Kaocha test aliases
            ;; refer to tests.edn for test configuration
            "kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]
            "itest" ["kaocha" "--focus" ":integration"]
            "utest" ["kaocha" "--focus" ":unit"]
            "ci-test" ["kaocha" "--profile" ":ci"]
            "ci-itest" ["itest" "--profile" ":ci"]
            "ci-utest" ["utest" "--profile" ":ci"]

            ;; Linting aliases
            "kibit" ["do"
                     ["with-profile" "lint" "shell" "echo" "== Kibit =="]
                     ["with-profile" "lint" "kibit"]]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "check-deps" ["with-profile" "lint" "ancient" ":all"]
            "check-sec" ["with-profile" "security" "dependency-check"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]
            ;; Placeholder for future docs and enabler of top-level alias
            "generate-static" ["with-profile" "static" "shell" "echo"]})
