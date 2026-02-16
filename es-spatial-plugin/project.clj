#_{:clj-kondo/ignore [:unresolved-namespace]}
(def version
  "Parses the version out of this file to use in names of referenced files"
  (let [project-clj-lines (-> "project.clj" slurp (clojure.string/split #"\n"))]
    (-> (filter (partial re-find #"^\(defproject") project-clj-lines)
        first
        (clojure.string/split #" ")
        last
        (clojure.string/replace "\"" ""))))

(def plugin-jar-name
  (str "target/cmr-es-spatial-plugin-" version ".jar"))

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

(defn get-list-of-dep-jars []
  (let [all-jars (into [] (map #(.getName %) (.listFiles (clojure.java.io/file "target/lib"))))
        ;; Minimal set: only what spatial-lib Java code actually needs
        allowed-prefixes ["clojure"           ; Runtime (spatial-lib has compiled Clojure)
                          "cmr-spatial-lib"   ; Main library
                          "jafama"            ; Math library used by spatial calculations
                          "primitive-math"    ; Math optimizations
                          "vectorz"]]         ; Vector math library
    (map #(str "target/lib/" %) (filter (fn [jar-name] (some (fn [prefix] (str/starts-with? jar-name prefix)) allowed-prefixes)) all-jars))))

(defproject nasa-cmr/cmr-es-spatial-plugin "0.1.0-SNAPSHOT"
  :description "A Elastic Search plugin that enables spatial search entirely within elastic."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/es-spatial-plugin"
  :java-source-paths ["src/java"]
  :javac-options ["-target" "11" "-source" "11"]
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :plugins [[lein-shell "0.5.0"]]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.4.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"}}
             :provided {:dependencies [[nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                                       [nasa-cmr/cmr-spatial-lib "0.1.0-SNAPSHOT"]
                                       [org.elasticsearch/elasticsearch "8.18.7"]]}
             :es-deps {:dependencies [[nasa-cmr/cmr-spatial-lib "0.1.0-SNAPSHOT"
                                       ;; These exclusions will be provided by elasticsearch.
                                       :exclusions [[com.dadrox/quiet-slf4j]
                                                    [com.fasterxml.jackson.core/jackson-core]
                                                    [com.fasterxml.jackson.dataformat/jackson-dataformat-cbor]
                                                    [com.fasterxml.jackson.dataformat/jackson-dataformat-smile]
                                                    [com.fasterxml.jackson.dataformat/jackson-dataformat-yaml]
                                                    [commons-io]
                                                    [commons-codec]
                                                    [commons-logging]
                                                    [joda-time]
                                                    [org.ow2.asm/asm]
                                                    [org.ow2.asm/asm-all]
                                                    [net.jpountz.lz4/lz4]
                                                    [org.locationtech.jts/jts-core]
                                                    [org.locationtech.jts.JTSVersion]
                                                    [org.slf4j/slf4j-api]]]
                                      [org.clojure/tools.reader "1.5.0"]
                                      [org.clojure/clojure "1.11.2"]]
                       :target-path ~es-deps-target-path
                       :uberjar-name ~es-deps-uberjar-name
                       :jar-name ~es-deps-jar-name
                       :aot []}
             :jar-deps {:plugins [[org.clojars.jj/copy-deps "1.0.1"]]
                        :dependencies [[nasa-cmr/cmr-spatial-lib "0.1.0-SNAPSHOT"]
                                       [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]]
                        :aot []}
             :dev {:dependencies [[criterium "0.4.4"]
                                  [cheshire "5.13.0"]
                                  [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                                  [nasa-cmr/cmr-spatial-lib "0.1.0-SNAPSHOT"]
                                  [org.elasticsearch/elasticsearch "8.18.7"]
                                  [org.clojars.gjahad/debug-repl "0.3.3"]
                                  [nrepl/nrepl "1.3.0"]
                                  [org.clojure/tools.namespace "1.2.0"]]
                   :global-vars {*warn-on-reflection* false
                                 *assert* false}}
             :static {}
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
  :aliases {"install-es-plugin" ["do"
                                 ["shell" "echo" "Building ES spatial plugin JAR"]
                                 "with-profile" "provided" "clean,"
                                 "with-profile" "provided" "jar,"]
            "gather-dependencies" ["do"
                                 ["shell" "echo" "Collecting dependent JARs"]
                                   "with-profile" "jar-deps" "copy-deps,"]
            "prepare-es-plugin" ["do"
                                 "install-es-plugin"
                                 "gather-dependencies"]
            "package-es-plugin" ~(vec (concat ["do"
                                               ["shell" "echo" "Packaging ES plugin into zip file"]
                                               "shell"
                                               "zip"
                                               "-j"
                                               plugin-zip-name
                                               plugin-jar-name
                                               "resources/plugin/plugin-descriptor.properties"]
                                              (get-list-of-dep-jars)))
            "build-all" ["do"
                         ["shell" "echo" "build-all"]
                         "install-es-plugin,"]

            ;; Kaocha test aliases
            ;; refer to tests.edn for test configuration
            "kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]
            "itest" ["kaocha" "--focus" ":integration"]
            "utest" ["kaocha" "--focus" ":unit"]
            "ci-test" ["kaocha" "--profile" ":ci"]
            "ci-itest" ["itest" "--profile" ":ci"]
            "ci-utest" ["utest" "--profile" ":ci"]

            "package-all" ["do"
                           ["shell" "echo" "package-all"]
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
            "generate-static" ["with-profile" "static" "shell" "echo" "no generate-static action needed"]})
