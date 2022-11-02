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

(defproject gov.nasa.earthdata/cmr-api-versioning "0.1.1-SNAPSHOT"
  :description "A CMR utility library allowing services to provide versioned REST APIs"
  :url "https://github.com/cmr-exchange/cmr-api-versioning"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[clojusc/trifl "0.4.0"]
                 [clojusc/twig "0.4.0"]
                 [com.stuartsierra/component "0.3.2"]
                 [gov.nasa.earthdata/cmr-exchange-common "0.3.3"]
                 [gov.nasa.earthdata/cmr-http-kit "0.2.0"]
                 [metosin/reitit-ring "0.2.4"]
                 [org.clojure/clojure "1.9.0"]]
  :manifest {"CMR-Plugin" "service-bridge-app"}
  :jvm-opts ["-XX:-OmitStackTraceInFastThrow"
             "-Xms2g"
             "-Xmx2g"]
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
                                     [com.google.javascript/closure-compiler-unshaded]
                                     ;; The following is excluded because it stomps on twig's logger
                                     [org.slf4j/slf4j-simple]]}
             :system {:dependencies [[clojusc/system-manager "0.3.0"]]}
             :local {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                                    [proto-repl "0.3.1"]]
                     :plugins [[lein-project-version "0.1.0"]
                               [lein-shell "0.5.0"]
                               [venantius/ultra "0.5.2"]]
                     :source-paths ["dev-resources/src"]
                     :jvm-opts ["-Dlogging.color=true"]}
             :dev {:dependencies [[debugger "0.2.1"]]
                   :repl-options {:init-ns cmr.ous.repl
                                  :prompt ~get-prompt
                                  :init ~(println (get-banner))}}
             :lint {:source-paths ^:replace ["src"]
                    :test-paths ^:replace []
                    :plugins [[jonase/eastwood "0.3.1"]
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
                       ["shell" "echo" "-n" "CMR OUS: "]
                       ["project-version"]]
            "ubercompile" ["with-profile" "+system,+local,+ubercompile" "compile"]
            "uberjar" ["with-profile" "+system" "uberjar"]
            "uberjar-aot" ["with-profile" "+system,+ubercompile" "uberjar"]
            "check-vers" ["with-profile" "+lint" "ancient" "check" ":all"]
            "check-jars" ["with-profile" "+lint" "do"
                          ["deps" ":tree"]
                          ["deps" ":plugin-tree"]]
            "check-deps" ["do"
                          ["check-jars"]
                          ["check-vers"]]
            "kibit" ["with-profile" "+lint" "kibit"]
            "eastwood" ["with-profile" "+lint" "eastwood" "{:namespaces [:source-paths]}"]
            "lint" ["do"
                    ["kibit"]]
                    ;["eastwood"]
            "ltest" ["with-profile" "+test,+system" "ltest"]
            "junit" ["with-profile" "+test,+system" "test2junit"]
            ;; Security
            "check-sec" ["with-profile" "+system,+local,+security" "do"
                         ["clean"]
                         ["dependency-check"]]
            ;; Build tasks
            "build-jar" ["with-profile" "+security" "jar"]
            "build-uberjar" ["with-profile" "+security" "uberjar"]
            "build-lite" ["do"
                          ["clean"]
                          ["lint"]
                          ["ltest" ":all"]
                          ["ubercompile"]]
            "build" ["do"
                     ["clean"]
                     ["lint"]
                     ["check-vers"]
                     ["check-sec"]
                     ["ltest" ":all"]
                     ["junit" ":all"]
                     ["ubercompile"]
                     ["build-uberjar"]]
            ;; Publishing
            "publish" ["with-profile" "+system,+security" "do"
                       ["clean"]
                       ["build-jar"]
                       ["deploy" "clojars"]]})
