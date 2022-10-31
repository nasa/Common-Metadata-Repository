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

(defproject gov.nasa.earthdata/cmr-exchange-geo "0.1.0"
  :description "A general geographic library that unifies separate libs under a common interface"
  :url "https://github.com/cmr-exchange/cmr-exchange-geo"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :exclusions [[net.sf.geographiclib/GeographicLib-Java]]
  :dependencies [[clojusc/trifl "0.4.2"]
                 [clojusc/twig "0.4.0"]
                 [com.esri.geometry/esri-geometry-api "2.2.1"]
                 [org.locationtech.jts/jts-core "1.19.0"]
                 [net.sf.geographiclib/GeographicLib-Java "1.49"]
                 [org.clojure/clojure "1.9.0"]
                 [org.geotools/gt-geometry "24.6"]
                 [org.geotools/gt-referencing "24.6"]]
  :repositories [["osgeo" "https://download.osgeo.org/webdav/geotools"]
                 ["geo" "https://repo.osgeo.org/repository/release"]
                 ["jts" "https://mvnrepository.com/artifact"]]
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
             :local {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                                    [proto-repl "0.3.1"]]
                     :plugins [[lein-shell "0.5.0"]
                               [venantius/ultra "0.5.2"]]
                     :source-paths ["dev-resources/src"]
                     :jvm-opts ["-Dlogging.color=true"]}
             :dev {:repl-options {:init-ns cmr.exchange.geo.dev
                                  :prompt ~get-prompt
                                  :init ~(println (get-banner))}}
             :lint {:source-paths ^:replace ["src"]
                    :test-paths ^:replace []
                    :plugins [[jonase/eastwood "0.3.3"]
                              [lein-ancient "0.6.15"]
                              [lein-bikeshed "0.5.1"]
                              [lein-kibit "0.1.8"]
                              [venantius/yagni "0.1.6"]]}
             :test {:dependencies [[clojusc/ltest "0.3.0"]]
                    :plugins [[lein-ltest "0.3.0"]
                              [test2junit "1.4.2"]
                              [venantius/ultra "0.5.2"]]
                    :test2junit-output-dir "junit-test-results"
                    :test-selectors {:unit #(not (or (:integration %) (:system %)))
                                     :integration :integration
                                     :system :system
                                     :default (complement :system)}}}
  :aliases {
            ;; Dev & Testing Aliases
            "repl" ["do"
                    ["clean"]
                    ["with-profile" "+local" "repl"]]
            "ubercompile" ["with-profile" "+ubercompile,+local,+security" "compile"]
            "uberjar-aot" ["with-profile" "+ubercompile,+security" "uberjar"]
            "check-vers" ["with-profile" "+lint,+security" "ancient" "check" ":all"]
            "check-jars" ["with-profile" "+lint" "do"
                          ["deps" ":tree"]
                          ["deps" ":plugin-tree"]]
            "check-deps" ["do"
                          ["check-jars"]
                          ["check-vers"]]
            "kibit" ["with-profile" "+lint" "kibit"]
            "eastwood" ["with-profile" "+lint" "eastwood" "{:namespaces [:source-paths]}"]
            "yagni" ["with-profile" "+lint" "yagni"]
            "lint" ["do"
                    ["kibit"]]
            ;["eastwood"]

            "ltest" ["with-profile" "+test,+local" "ltest"]
            "junit" ["with-profile" "+test,+local" "test2junit"]
            ;; Security
            "check-sec" ["with-profile" "+local,+security" "do"
                         ["clean"]
                         ["dependency-check"]]
            ;; Build tasks
            "build-jar" ["with-profile" "+security" "jar"]
            "build-uberjar" ["with-profile" "+security" "uberjar"]
            "build-lite" ["do"
                          ["clean"]
                          ["lint"]
                          ["ltest" ":unit"]
                          ["ubercompile"]]
            "build" ["do"
                     ["clean"]
                     ["lint"]
                     ["check-vers"]
                     ["check-sec"]
                     ["ltest" ":unit"]
                     ["junit" ":unit"]
                     ["ubercompile"]
                     ["build-uberjar"]]
            ;; Publishing
            "publish" ["with-profile" "+security" "do"
                       ["clean"]
                       ["build-jar"]
                       ["deploy" "clojars"]]
            ;; Application
            "run" ["with-profile" "+security" "run"]
            "trampoline" ["with-profile" "+security" "trampoline"]
            "start-service-bridge" ["trampoline" "run"]})
