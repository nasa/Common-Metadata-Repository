(defproject gov.nasa.earthdata/cmr-authz "0.1.3"
  :description "An authorization utility library for CMR services"
  :url "https://github.com/cmr-exchange/authz"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[cheshire "5.8.1"]
                 [clojusc/trifl "0.4.2"]
                 [clojusc/twig "0.4.0"]
                 [com.stuartsierra/component "0.3.2"]
                 [gov.nasa.earthdata/cmr-exchange-common "0.3.3"]
                 [gov.nasa.earthdata/cmr-http-kit "0.2.0"]
                 [http-kit "2.5.3"]
                 [metosin/reitit-ring "0.2.7"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/core.cache "0.7.1"]
                 [org.clojure/data.xml "0.2.0-alpha5"]
                 [tolitius/xml-in "0.1.0"]]
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
             :lint {:source-paths ^:replace ["src"]
                    :test-paths ^:replace []
                    :plugins [[jonase/eastwood "0.3.3"]
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
            "build-full" ["do"
                          ["ltest" ":unit"]
                          ["ubercompile"]
                          ["build-uberjar"]]
            ;; Publishing
            "publish" ["with-profile" "+security" "do"
                       ["clean"]
                       ["build-jar"]
                       ["deploy" "clojars"]]})
