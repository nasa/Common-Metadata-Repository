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
       "\u001B[35m]\u001B[33m =>\u001B[m "))

(defproject gov.nasa.earthdata/cmr-nlp "0.1.0-SNAPSHOT"
  :description "A service for converting natural language queries into CMR search parameters"
  :url "https://github.com/cmr-exchange/cmr-nlp"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[cheshire "5.8.1"]
                 [clojusc/trifl "0.4.2"]
                 [clojusc/twig "0.4.0"]
                 [clojure-opennlp "0.5.0"]
                 [com.neovisionaries/nv-i18n "1.23"]
                 [com.stuartsierra/component "0.3.2"]
                 [gov.nasa.earthdata/cmr-exchange-common "0.3.3"]
                 [gov.nasa.earthdata/cmr-mission-control "0.1.0"]
                 [org.apache.commons/commons-csv "1.6"]
                 [org.clojure/clojure "1.10.0"]
                 [org.elasticsearch.client/elasticsearch-rest-high-level-client "6.5.3"]
                 [org.ocpsoft.prettytime/prettytime "4.0.2.Final"]
                 [org.ocpsoft.prettytime/prettytime-nlp "4.0.2.Final"]]
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
             :local {:resource-paths ["data"]
                     :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                    [proto-repl "0.3.1"]]
                     :plugins [[lein-project-version "0.1.0"]
                               [lein-shell "0.5.0"]
                               [venantius/ultra "0.5.2"]]
                     :source-paths ["dev-resources/src"]
                     :jvm-opts ["-Dlogging.color=true"]}
             :dev {:dependencies [[debugger "0.2.1"]]
                   :repl-options {:init-ns cmr.nlp.repl
                                  :prompt ~get-prompt
                                  :init ~(println (get-banner))}}
             :lint {:source-paths ^:replace ["src"]
                    :test-paths ^:replace []
                    :plugins [[jonase/eastwood "0.3.4"]
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
                                     :default (complement :system)}}
             :ingest {:main cmr.nlp.elastic.ingest
                      :jvm-opts ^replace ["-Dlogging.level=debug"
                                          "-Dlogging.color=true"]}}
  :aliases {
            ;; Dev & Testing Aliases
            "download-models" ["with-profile" "+local"
                               "shell" "resources/scripts/download-models"]
            "download-geonames" ["with-profile" "+local"
                                 "shell" "resources/scripts/download-geonames"]
            "delete-models" ["with-profile" "+local"
                             "shell" "rm" "-rf" "data/models"]
            "repl" ["do"
                    ["clean"]
                    ["with-profile" "+local,+system" "repl"]]
            "ubercompile" ["with-profile" "+system,+local,+security,+ubercompile" "do"
                           ["clean"]
                           ["compile"]]
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
            "ltest" ["with-profile" "+test,+system,+local" "ltest"]
            "junit" ["with-profile" "+test,+system" "test2junit"]
            ;; Security
            "check-sec" ["with-profile" "+system,+local,+security" "do"
                         ["clean"]
                         ["dependency-check"]]
            ;; Docker Aliases
            "docker-clean" ["with-profile" "+system,+local,+security" "do"
                            ["shell" "docker" "system" "prune" "-f"]]
            "start-es" ["with-profile" "+system,+local,+security" "do"
                        ["shell" "docker-compose" "-f" "resources/elastic/docker-compose.yml" "up"]]
            "stop-es" ["with-profile" "+system,+local,+security" "do"
                       ["shell" "docker-compose" "-f" "resources/elastic/docker-compose.yml" "down"]]
            ;; Build tasks
            "build-jar" ["with-profile" "+system,+security" "do"
                         ["jar"]]
            "build-uberjar" ["with-profile" "+system,+security" "do"
                             ["uberjar"]]
            "build" ["do"
                     ["clean"]
                     ["check-vers"]
                     ["download-models"]
                     ["ubercompile"]
                     ;; XXX This broke with the introduction of the Elasticsearch dep
                     ;;["check-sec"]
                     ["ltest" ":unit"]
                     ["build-uberjar"]]
            ;; CLI
            "ingest" ["with-profile" "+ingest,+local,+system" "do"
                      ["clean"]
                      ["trampoline" "run"]]
            ;; Publishing
            "publish" ["with-profile" "+security" "do"
                       ["clean"]
                       ["build-jar"]
                       ["deploy" "clojars"]]})
