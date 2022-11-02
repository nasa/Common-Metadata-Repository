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

(defproject gov.nasa.earthdata/cmr-service-bridge "1.6.13-SNAPSHOT"
  :description "A CMR connector service that provides an inter-service API"
  :url "https://github.com/cmr-exchange/cmr-service-bridge"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :exclusions [[org.clojure/clojurescript]
               [org.eclipse.emf/org.eclipse.emf.ecore]]
  :dependencies [[cheshire "5.10.0"]
                 [clojusc/trifl "0.4.2"]
                 [clojusc/twig "0.4.1"]
                 [com.fasterxml.jackson.core/jackson-core "2.12.1"]
                 [com.fasterxml.jackson.dataformat/jackson-dataformat-cbor "2.12.1"
                  :exclusions [com.fasterxml.jackson.core/jackson-databind]]
                 [com.fasterxml.jackson.core/jackson-databind "2.13.2.1"]
                 [com.stuartsierra/component "0.4.0"]
                 [environ "1.1.0"]
                 [gov.nasa.earthdata/cmr-api-versioning "0.1.1-SNAPSHOT"]
                 [gov.nasa.earthdata/cmr-authz "0.1.3"]
                 [gov.nasa.earthdata/cmr-exchange-common "0.3.3"]
                 [gov.nasa.earthdata/cmr-exchange-query "0.3.3-SNAPSHOT"]
                 [gov.nasa.earthdata/cmr-http-kit "0.2.0"]
                 [gov.nasa.earthdata/cmr-jar-plugin "0.1.2"]
                 [gov.nasa.earthdata/cmr-metadata-proxy "0.2.8-SNAPSHOT"]
                 [gov.nasa.earthdata/cmr-mission-control "0.1.0"]
                 [gov.nasa.earthdata/cmr-ous-plugin "0.3.8-SNAPSHOT"]
                 [gov.nasa.earthdata/cmr-site-templates "0.1.0"]
                 [gov.nasa.earthdata/cmr-sizing-plugin "0.3.5-SNAPSHOT"]
                 [http-kit "2.5.3"]
                 [markdown-clj "1.10.0"]
                 [metosin/reitit-core "0.3.9"]
                 [metosin/reitit-ring "0.3.9"]
                 [metosin/ring-http-response "0.9.1"]
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.891"]
                 [org.clojure/core.async "0.4.500"]
                 [org.clojure/core.cache "0.7.2"]
                 [org.clojure/data.xml "0.2.0-alpha5"]
                 [org.clojure/java.classpath "0.3.0"]
                 [org.geotools/gt-geometry "24.6"]
                 [org.geotools/gt-referencing "24.6"]
                 [org.yaml/snakeyaml "1.31"]
                 [ring/ring-core "1.7.1"]
                 [ring/ring-codec "1.1.2"]
                 [ring/ring-defaults "0.3.2"]
                 [selmer "1.12.12"]
                 [tolitius/xml-in "0.1.0"]]
  :repositories [["geo" "https://repo.osgeo.org/repository/release/"]]
  :jvm-opts ["-XX:-OmitStackTraceInFastThrow"
             "-Xms2g"
             "-Xmx2g"]
  :main cmr.opendap.core
  :aot [clojure.tools.logging.impl
        cmr.opendap.core]
  :profiles {:ubercompile {:aot :all
                           :source-paths ["test"]}
             :security {:plugins [[com.livingsocial/lein-dependency-check "1.1.2"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"}
                        :source-paths ^:replace ["src"]
                        :exclusions [
                                     ;; The following are excluded due to their being flagged as a CVE
                                     [com.google.protobuf/protobuf-java]
                                     [com.google.javascript/closure-compiler-unshaded]
                                     ;; The following is excluded because it stomps on twig's logger
                                     [org.slf4j/slf4j-simple]]}
             :geo {:dependencies [[gov.nasa.earthdata/cmr-exchange-geo "0.1.0"]]}
             :system {:dependencies [[clojusc/system-manager "0.3.0"]]}
             :local {:dependencies [[org.clojure/tools.namespace "0.3.0" :exclusions [org.clojure/tools.reader]]
                                    [proto-repl "0.3.1"]]
                     :plugins [[lein-project-version "0.1.0"]
                               [lein-shell "0.5.0"]
                               [venantius/ultra "0.6.0"]]
                     :source-paths ["dev-resources/src"]
                     :jvm-opts ["-Dlogging.color=true"]}
             :dev {:dependencies [[debugger "0.2.1"]]
                   :repl-options {:init-ns cmr.opendap.dev
                                  :prompt ~get-prompt
                                  :timeout 120000
                                  :init ~(println (get-banner))}}
             :lint {:source-paths ^:replace ["src"]
                    :test-paths ^:replace []
                    :plugins [[jonase/eastwood "0.3.5"]
                              [lein-ancient "0.6.15"]
                              [lein-bikeshed "0.5.2"]
                              [lein-kibit "0.1.8"]
                              [venantius/yagni "0.1.7"]]}
             :test {:dependencies [[clojusc/ltest "0.3.0"]]
                    :plugins [[lein-ltest "0.3.0"]
                              [test2junit "1.4.2"]
                              [venantius/ultra "0.6.0"]]
                    :jvm-opts ["-Dcmr.testing.config.data=testing-value"]
                    :test2junit-output-dir "junit-test-results"
                    :test-selectors {:unit #(not (or (:integration %) (:system %)))
                                     :integration :integration
                                     :system :system
                                     :default (complement :system)}}
             :kaocha {:dependencies [[lambdaisland/kaocha "1.0.732"]
                                     [lambdaisland/kaocha-cloverage "1.0.75"]
                                     [lambdaisland/kaocha-junit-xml "0.0.76"]]}
             :docs {:dependencies [[gov.nasa.earthdata/codox-theme "1.0.0-SNAPSHOT"]]
                    :plugins [[lein-codox "0.10.7"]
                              [lein-marginalia "0.9.1"]]
                    :source-paths ["resources/docs/src"]
                    :codox {:project {:name "CMR Service-Bridge"
                                      :description "A CMR connector service that provides an inter-service API"}
                            :namespaces [#"^cmr\.opendap\.(?!dev)"]
                            :metadata {:doc/format :markdown
                                       :doc "Documentation forthcoming"}
                            :themes [:eosdis]
                            :html {:transforms [[:head]
                                                [:append
                                                  [:script {:src "https://cdn.earthdata.nasa.gov/tophat2/tophat2.js"
                                                            :id "earthdata-tophat-script"
                                                            :data-show-fbm "true"
                                                            :data-show-status "true"
                                                            :data-status-api-url "https://status.earthdata.nasa.gov/api/v1/notifications"
                                                            :data-status-polling-interval "10"}]]
                                                [:body]
                                                [:prepend
                                                  [:div {:id "earthdata-tophat2"
                                                         :style "height: 32px;"}]]
                                                [:body]
                                                [:append
                                                  [:script {:src "https://fbm.earthdata.nasa.gov/for/CMR/feedback.js"
                                                            :type "text/javascript"}]]]}
                            :doc-paths ["resources/docs/markdown"]
                            :output-path "resources/public/docs/service-bridge/docs/current/reference"}}
             :slate {:plugins [[lein-shell "0.5.0"]]}}
  :aliases {
            ;; Dev & Testing Aliases
            "repl" ["do"
                    ["clean"]
                    ["with-profile" "+local,+system" "repl"]]
            "repl-geo" ["do"
                        ["clean"]
                        ["with-profile" "+local,+system,+geo" "repl"]]
            "version" ["do"
                       ["version"]
                       ["shell" "echo" "-n" "CMR Service-Bridge: "]
                       ["project-version"]]
            "ubercompile" ["with-profile" "+system,+geo,+local,+security" "compile"]
            "uberjar" ["with-profile" "+system,+geo" "uberjar"]
            "uberjar-aot" ["with-profile" "+system,+geo,+ubercompile,+security" "uberjar"]
            "check-vers" ["with-profile" "+lint,+system,+geo,+security" "ancient" "check" ":all"]
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
            "ltest" ["with-profile" "+test,+system,+local" "ltest"]
            "junit" ["with-profile" "+test,+system,+local" "test2junit"]
            "ltest-with-geo" ["with-profile" "+test,+system,+geo,+local" "ltest"]
            "junit-with-geo" ["with-profile" "+test,+system,+geo,+local" "test2junit"]

            ;; Kaocha test aliases
            ;; refer to tests.edn for test configuration
            "kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]
            "itest" ["kaocha" "--focus" ":integration"]
            "stest" ["kaocha" "--focus" ":system"]
            "utest" ["kaocha" "--focus" ":unit"]
            "ci-test" ["kaocha" "--profile" ":ci"]
            "ci-itest" ["itest" "--profile" ":ci"]
            "ci-stest" ["stest" "--profile" ":ci"]
            "ci-utest" ["utest" "--profile" ":ci"]

            ;; Security
            "check-sec" ["with-profile" "+system,+geo,+local,+security" "do"
                         ["clean"]
                         ["dependency-check"]]
            ;; Documentation and static content
            "codox" ["with-profile" "+docs,+system,+geo" "codox"]
            "marginalia" ["with-profile" "+docs,+system,+geo"
                          "marg" "--dir" "resources/public/docs/service-bridge/docs/current/marginalia"
                          "--file" "index.html"
                          "--name" "CMR integration with external services"]
            "slate" ["with-profile" "+slate"
                     "shell" "resources/scripts/build-slate-docs"]
            "docs" ["do"
                    ["codox"]
                    ["marginalia"]
                    ["slate"]]
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
            ;; Build without version or security check.
            "build-no-check" ["do"
                              ["clean"]
                              ["lint"]
                              ["ltest" ":unit"]
                              ["junit" ":unit"]
                              ["ubercompile"]
                              ["build-uberjar"]]
            "build-full" ["do"
                          ["build"]
                          ["docs"]]
            ;; Publishing
            "publish" ["with-profile" "+system,+security,+geo" "do"
                       ["clean"]
                       ["build-jar"]
                       ["deploy" "clojars"]]
            ;; Application
            "run" ["with-profile" "+system,+security" "run"]
            "trampoline" ["with-profile" "+system,+security" "trampoline"]
            "start-service-bridge" ["trampoline" "run"]})
