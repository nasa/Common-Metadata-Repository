(defn get-banner
  []
  (try
    (str
      (slurp "resources/text/banner.txt"))
      ;(slurp "resources/text/loading.txt")

    ;; If another project can't find the banner, just skip it;
    ;; this function is really only meant to be used by Dragon itself.
    (catch Exception _ "")))

(defn get-prompt
  [ns]
  (str "\u001B[35m[\u001B[34m"
       ns
       "\u001B[35m]\u001B[33m λ\u001B[m=> "))

(defproject gov.nasa.earthdata/cmr-jar-plugin "0.1.2"
  :description "A library for creating JAR-based plugins"
  :url "https://github.com/cmr-exchange/cmr-jar-plugin"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[clojusc/trifl "0.4.2"]
                 [clojusc/twig "0.4.0"]
                 [com.stuartsierra/component "0.3.2"]
                 [gov.nasa.earthdata/cmr-exchange-common "0.3.3"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/java.classpath "0.3.0"]]
  :aot [clojure.tools.logging.impl]
  :profiles {:ubercompile {:aot :all
                           :source-paths ["test"]}
             :security {:plugins [[com.livingsocial/lein-dependency-check "1.1.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"}
                        :source-paths ^:replace ["src"]
                        :exclusions [
                                     ;; The following are excluded due to their being flagged as a CVE
                                     [com.google.protobuf/protobuf-java]
                                     [com.google.javascript/closure-compiler-unshaded]]}
             :system {:dependencies [[clojusc/system-manager "0.3.0"]]}
             :local {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                                    [proto-repl "0.3.1"]]
                     :plugins [[lein-project-version "0.1.0"]
                               [lein-shell "0.5.0"]
                               [venantius/ultra "0.5.2"]]
                     :source-paths ["dev-resources/src"]}
             :dev {:dependencies [[debugger "0.2.1"]]
                   :repl-options {:init-ns cmr.plugin.jar.dev
                                  :prompt ~get-prompt
                                  :init ~(println (get-banner))}}
             :lint {:source-paths ^:replace ["src"]
                    :test-paths ^:replace []
                    :plugins [[jonase/eastwood "0.3.3"]
                              [lein-ancient "0.6.15"]
                              [lein-bikeshed "0.5.1"]
                              [lein-kibit "0.1.8"]]}
             :test {:dependencies [[clojusc/ltest "0.3.0"]]
                    :plugins [[lein-ltest "0.3.0"]
                              [test2junit "1.4.2"]]
                    :test2junit-output-dir "junit-test-results"
                    :test-selectors {:unit #(not (or (:integration %) (:system %)))
                                     :integration :integration
                                     :system :system
                                     :default (complement :system)}}}
  :aliases {
            ;; Dev & Testing Aliases
            "repl" ["do"
                    ["clean"]
                    ["with-profile" "+local,+system" "repl"]]
            "version" ["do"
                       ["version"]
                       ["shell" "echo" "-n" "CMR-OPeNDAP: "]
                       ["project-version"]]
            "ubercompile" ["with-profile" "+system,+local,+ubercompile" "compile"]
            "check-vers" ["with-profile" "+lint" "ancient" "check" ":all"]
            "check-jars" ["with-profile" "+lint" "do"
                          ["deps" ":tree"]
                          ["deps" ":plugin-tree"]]
            "check-deps" ["do"
                          ["check-jars"]
                          ["check-vers"]]
            "ltest" ["with-profile" "+test,+system" "ltest"]
            "junit" ["with-profile" "+test,+system" "test2junit"]
            ;; Lintint
            "kibit" ["with-profile" "+lint" "kibit"]
            "eastwood" ["with-profile" "+lint" "eastwood" "{:namespaces [:source-paths]}"]
            "lint" ["do"
                    ["kibit"]]
            ;["eastwood"]

            ;; Security
            "check-sec" ["with-profile" "+security,+system" "do"
                         ["clean"]
                         ["dependency-check"]]
            ;; Build tasks
            "build-jar" ["with-profile" "+security,+system" "jar"]
            "build-uberjar" ["with-profile" "+security,+system" "uberjar"]
            "build-uberjar-aot" ["with-profile" "+security,+system,+ubercompile" "uberjar"]
            "build-lite" ["do"
                          ["ltest" ":unit"]]
            "build" ["do"
                     ["clean"]
                     ["check-vers"]
                     ["check-sec"]
                     ["ltest" ":unit"]
                     ["ubercompile"]
                     ["build-uberjar"]]
            ;; Publishing
            "publish" ["with-profile" "+security,+system" "do"
                       ["clean"]
                       ["build-jar"]
                       ["deploy" "clojars"]]})
