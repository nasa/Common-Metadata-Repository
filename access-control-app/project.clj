(def projects
  "A map of the other development projects to their versions"
  {:cmr-acl-lib "0.1.0-SNAPSHOT"
   :cmr-common-app-lib "0.1.0-SNAPSHOT"
   :cmr-common-lib "0.1.1-SNAPSHOT"
   :cmr-elastic-utils-lib "0.1.0-SNAPSHOT"
   :cmr-message-queue-lib "0.1.0-SNAPSHOT"
   :cmr-metadata-db-app "0.1.0-SNAPSHOT"
   :cmr-mock-echo-app "0.1.0-SNAPSHOT"
   :cmr-transmit-lib "0.1.0-SNAPSHOT"
   :cmr-umm-spec-lib "0.1.0-SNAPSHOT"})

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

(defproject nasa-cmr/cmr-access-control-app "0.1.0-SNAPSHOT"
  :description "Implements the CMR access control application."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/access-control-app"
  :exclusions [[cheshire]
               [clj-time]
               [com.fasterxml.jackson.core/jackson-core]
               [commons-codec/commons-codec]
               [commons-io]
               [org.clojure/tools.reader]
               [ring/ring-codec]]
  :dependencies ~(concat '[[cheshire "5.10.0"]
                           [clj-time "0.15.1"]
                           [com.fasterxml.jackson.core/jackson-core "2.13.2"]
                           [com.fasterxml.jackson.core/jackson-databind "2.13.2.1"]
                           [com.fasterxml.jackson.dataformat/jackson-dataformat-cbor "2.13.2"]
                           [commons-codec/commons-codec "1.11"]
                           [commons-io "2.6"]
                           [compojure "1.6.1"]
                           [gov.nasa.earthdata/cmr-site-templates "0.1.1-SNAPSHOT"]
                           [org.clojure/clojure "1.10.0"]
                           [org.clojure/tools.reader "1.3.2"]
                           [ring/ring-codec "1.1.3"]
                           [ring/ring-core "1.9.2"]
                           [ring/ring-json "0.5.1"]]
                         project-dependencies)
  :plugins [[lein-exec "0.3.7"]
            [lein-shell "0.5.0"]
            [test2junit "1.3.3"]]
  :repl-options {:init-ns user}
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :test-paths ["test" "int-test"]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.1.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"}}
             :dev {:exclusions [[org.clojure/tools.nrepl]]
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/tools.nrepl "0.2.13"]
                                  [pjstadig/humane-test-output "0.9.0"]
                                  [proto-repl "0.3.1"]
                                  [ring-mock "0.1.5"]]
                   :jvm-opts ^:replace ["-server"]
                   :source-paths ["src" "dev" "test" "int-test"]
                   :test-paths ["test" "int-test"]
                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]}
             ;; This profile specifically here for generating documentation. It's faster
             ;; than using the regular profile. An agent pool is being started when
             ;; using the default profile which causes the wait of 60 seconds before
             ;; allowing the JVM to shutdown since no call to shutdown-agents is made.
             ;; Generate docs with: lein generate-static (the alias makes use of the
             ;; static profile).
             :static {}
             :uberjar {:main cmr.access-control.runner
                       :aot :all}
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
  :aliases {"generate-static" ["with-profile" "static"
                               "run" "-m" "cmr.access-control.site.static" "all"]
            ;; Prints out documentation on configuration environment variables.
            "env-config-docs" ["exec" "-ep" "(do (use 'cmr.common.config) (print-all-configs-docs))"]
            "create-checkouts" ~create-checkouts-commands
            ;; Alias to test2junit for consistency with lein-test-out.
            "test-out" ["test2junit"]

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
                     ["with-profile" "lint" "kibit"]]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "check-deps" ["with-profile" "lint" "ancient" ":all"]
            "check-sec" ["with-profile" "security" "dependency-check"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]})
