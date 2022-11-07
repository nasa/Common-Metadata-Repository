(defn get-banner
  []
  (try
    (str
      (slurp "resources/text/banner.txt"))
      ;(slurp "resources/text/loading.txt")

    ;; If another project can't find the banner, just skip it.
    (catch Exception _ "")))

(defn get-prompt
  [ns]
  (str "\u001B[35m[\u001B[34m"
       ns
       "\u001B[35m]\u001B[33m λ\u001B[m=> "))

(defproject gov.nasa.earthdata/cmr-exchange-common "0.3.3"
  :description "Cross-project functionality, utilities, and general-use components"
  :url "https://github.com/cmr-exchange/cmr-exchange-common"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[clojusc/results "0.1.0"]
                 [clojusc/trifl "0.4.2"]
                 [clojusc/twig "0.4.0"]
                 [environ "1.1.0"]
                 [org.clojure/clojure "1.9.0"]]
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
             :dev {:dependencies [[clojusc/system-manager "0.3.0"]
                                  [org.clojure/java.classpath "0.3.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [proto-repl "0.3.1"]]
                   :plugins [[venantius/ultra "0.5.2"]]
                   :source-paths ["dev-resources/src"]
                   :repl-options {:init-ns cmr.exchange.common.dev
                                  :prompt ~get-prompt
                                  :init ~(println (get-banner))}}
             :lint {:source-paths ^:replace ["src"]
                    :test-paths ^:replace []
                    :plugins [[jonase/eastwood "0.3.4"]
                              [lein-ancient "0.6.15"]
                              [lein-kibit "0.1.8"]]}
             :test {:dependencies [[clojusc/ltest "0.3.0"]]
                    :plugins [[lein-ltest "0.3.0"]]
                    :test-selectors {:unit #(not (or (:integration %) (:system %)))
                                     :integration :integration
                                     :system :system
                                     :default (complement :system)}}}
  :aliases {
            ;; Dev & Testing Aliases
            "repl" ["do"
                    ["clean"]
                    ["repl"]]
            "ubercompile" ["with-profile" "+ubercompile,+security" "compile"]
            "check-vers" ["with-profile" "+lint" "ancient" "check" ":all"]
            "check-jars" ["with-profile" "+lint" "do"
                          ["deps" ":tree"]
                          ["deps" ":plugin-tree"]]
            "check-deps" ["do"
                          ["check-jars"]
                          ["check-vers"]]
            "ltest" ["with-profile" "+test,+system,+security" "ltest"]
            ;; Linting
            "kibit" ["with-profile" "+lint" "kibit"]
            "eastwood" ["with-profile" "+lint" "eastwood" "{:namespaces [:source-paths]}"]
            "lint" ["do"
                    ["kibit"]]
                    ;["eastwood"]
            ;; Security
            "check-sec" ["with-profile" "+security" "do"
                         ["clean"]
                         ["dependency-check"]]
            ;; Build tasks
            "build-jar" ["with-profile" "+security" "jar"]
            "build-uberjar" ["with-profile" "+security" "uberjar"]
            "build-lite" ["do"
                          ["ltest" ":unit"]]
            "build" ["do"
                     ["clean"]
                     ["check-vers"]
                     ["check-sec"]
                     ["ltest" ":unit"]
                     ["ubercompile"]
                     ["build-uberjar"]]
            ;; Installing
            "install" ["do"
                       ["clean"]
                       ["ubercompile"]
                       ["clean"]
                       ["install"]]
            ;; Publishing
            "publish" ["with-profile" "+security" "do"
                       ["clean"]
                       ["build-jar"]
                       ["deploy" "clojars"]]})
