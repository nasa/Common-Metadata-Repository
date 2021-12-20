(def version
  "Parses the version out of this file to use in names of referenced files"
  (let [project-clj-lines (-> "project.clj" slurp (clojure.string/split #"\n"))]
    (-> (filter (partial re-find #"^\(defproject") project-clj-lines)
        first
        (clojure.string/split #" ")
        last
        (clojure.string/replace "\"" ""))))

(def uberjar-name
  (str "target/cmr-es-spatial-plugin-" version "-standalone.jar"))

(def plugin-zip-name
  (str "target/cmr-es-spatial-plugin-" version ".zip"))

(def es-deps-uberjar-name
  (str "cmr-es-spatial-plugin-deps-" version "-standalone.jar"))

(def es-deps-jar-name
  (str "cmr-es-spatial-plugin-deps-" version ".jar"))

(def es-deps-target-path
  "es-deps")

(defproject nasa-cmr/cmr-es-spatial-plugin "0.1.0-SNAPSHOT"
  :description "A Elastic Search plugin that enables spatial search entirely within elastic."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/es-spatial-plugin"
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :plugins [[lein-shell "0.5.0"]
            [test2junit "1.3.3"]]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.1.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"}}
             :provided {:dependencies [[nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                                       [nasa-cmr/cmr-spatial-lib "0.1.0-SNAPSHOT"]
                                       [org.elasticsearch/elasticsearch "7.16.2"]]}
             :es-deps {:dependencies [[nasa-cmr/cmr-spatial-lib "0.1.0-SNAPSHOT"
                                       ;; These exclusions will be provided by elasticsearch.
                                       :exclusions [[com.dadrox/quiet-slf4j]
                                                    [com.fasterxml.jackson.core/jackson-core]
                                                    [com.fasterxml.jackson.dataformat/jackson-dataformat-cbor]
                                                    [com.fasterxml.jackson.dataformat/jackson-dataformat-smile]
                                                    [com.fasterxml.jackson.dataformat/jackson-dataformat-yaml]
                                                    [commons-codec]
                                                    [commons-logging]
                                                    [joda-time]
                                                    [org.ow2.asm/asm]
                                                    [org.ow2.asm/asm-all]
                                                    [org.locationtech.jts/jts-core]
                                                    [org.locationtech.jts.JTSVersion]
                                                    [org.slf4j/slf4j-api]]]
                                      [org.clojure/clojure "1.10.0"]]
                       :target-path ~es-deps-target-path
                       :uberjar-name ~es-deps-uberjar-name
                       :jar-name ~es-deps-jar-name
                       :aot []}
             :es-plugin {:aot [cmr.elasticsearch.plugins.spatial.script.core
                               cmr.elasticsearch.plugins.spatial.factory.lfactory
                               cmr.elasticsearch.plugins.spatial.factory.core
                               cmr.elasticsearch.plugins.spatial.engine.core
                               cmr.elasticsearch.plugins.spatial.plugin]}
             :dev {:exclusions [[org.clojure/tools.nrepl]]
                   :dependencies [[criterium "0.4.4"]
                                  [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                                  [nasa-cmr/cmr-spatial-lib "0.1.0-SNAPSHOT"]
                                  [org.elasticsearch/elasticsearch "7.16.2"]
                                  [org.clojars.gjahad/debug-repl "0.3.3"]
                                  [org.clojure/tools.nrepl "0.2.13"]
                                  [org.clojure/tools.namespace "0.2.11"]]
                   :aot [cmr.elasticsearch.plugins.spatial.script.core
                         cmr.elasticsearch.plugins.spatial.factory.lfactory
                         cmr.elasticsearch.plugins.spatial.factory.core
                         cmr.elasticsearch.plugins.spatial.engine.core
                         cmr.elasticsearch.plugins.spatial.plugin]
                   :global-vars {*warn-on-reflection* true
                                 *assert* false}}
             :static {}
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
  :aliases {"install-es-deps" ["do"
                               "with-profile" "es-deps,provided" "clean,"
                               "with-profile" "es-deps,provided" "uberjar,"
                               ;; target-path is being ignored for uberjar. move uberjar to es-deps-target-path.
                               "shell" "mv" ~(str "target/" es-deps-uberjar-name) ~es-deps-target-path]
            "install-es-plugin" ["do"
                                 "with-profile" "es-plugin,provided" "clean,"
                                 "with-profile" "es-plugin,provided" "uberjar,"]
            "package-es-plugin" ["do"
                                 "install-es-plugin"
                                 "shell"
                                 "zip"
                                 "-j"
                                 ~plugin-zip-name
                                 ~uberjar-name
                                 "resources/plugin/plugin-descriptor.properties"]
            "build-all" ["do"
                         "install-es-deps,"
                         "install-es-plugin,"]
            ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]

            ;; Kaocha test aliases
            ;; refer to tests.edn for test configuration
            "kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]
            "itest" ["kaocha" "--focus" ":integration"]
            "utest" ["kaocha" "--focus" ":unit"]
            "ci-test" ["kaocha" "--profile" ":ci"]
            "ci-itest" ["itest" "--profile" ":ci"]
            "ci-utest" ["utest" "--profile" ":ci"]

            "package-all" ["do"
                           "install-es-deps,"
                           "package-es-plugin,"
                           "shell"
                           "zip"
                           "-j"
                           "target/spatial-plugin-including-deps.zip"
                           ~plugin-zip-name
                           ~(str es-deps-target-path "/" es-deps-uberjar-name)]
            "check-sec" ["with-profile" "security" "dependency-check"]
            ;; Placeholder for future docs and enabler of top-level alias
            "generate-static" ["with-profile" "static" "shell" "echo"]})
