(defn print-welcome
  []
  (println (slurp "dev-resources/text/banner.txt"))
  (println (slurp "dev-resources/text/loading.txt")))

(defproject gov.nasa.earthdata/cmr-dev-env-manager "0.0.4-SNAPSHOT"
  :description "An Alternate Development Environment Manager for the CMR"
  :url "https://github.com/cmr-exchange/dev-env-manager"
  :license {:name "Apache License 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"}
  :exclusions [commons-codec
               instaparse
               org.apache.httpcomponents/httpclient
               org.apache.maven.wagon/wagon-provider-api
               org.clojure/clojure
               org.clojure/tools.macro]
  :dependencies [[cheshire "5.8.0"]
                 [clj-http "3.7.0"]
                 [com.stuartsierra/component "0.3.2"]
                 [commons-codec "1.11"]
                 [hawk "0.2.11"]
                 [instaparse "1.4.8"]
                 [leiningen-core "2.7.1" :exclusions [commons-io
                                                      org.apache.httpcomponents/httpcore
                                                      org.slf4j/slf4j-nop]]
                 [org.apache.httpcomponents/httpclient "4.5.13"]
                 [org.apache.maven.wagon/wagon-provider-api "2.10"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.3.465" :exclusions [org.clojure/tools.reader]]
                 [org.clojure/tools.macro "0.1.5"]]
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;;;   CMR D.E.M. specific configuration   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  :dem {:logging {:level :debug}
        :elastic-search {
                         ;:image-id "docker.elastic.co/elasticsearch/elasticsearch:6.0.1"
                         :image-id "elasticsearch:1.6.2"
                         :ports ["127.0.0.1:9200:9200" "127.0.0.1:9300:9300"]
                         :env ["discovery.type=single-node"]
                         :container-id-file "/tmp/cmr-dem-elastic-container-id"}
        :elastic-search-head {:image-id "mobz/elasticsearch-head:1"
                              :ports ["127.0.0.1:9100:9100"]
                              :container-id-file "/tmp/cmr-dem-elastic-head-container-id"}
        :enabled-services #{
                            ;; Support services
                            :elastic-search
                            ;:elastic-search-head
                            ;; CMR services
                            :mock-echo}
        :timer {:delay 1000}}
  :profiles {
              ;; Tasks
             :ubercompile {:aot :all}
             ;; Environments
             :dev {:dependencies [[clojusc/ltest "0.3.0-SNAPSHOT"]
                                  [clojusc/trifl "0.3.0-SNAPSHOT"]
                                  [clojusc/twig "0.3.2"]
                                  [gov.nasa.earthdata/cmr-process-manager "0.1.0-SNAPSHOT"]
                                  [me.raynes/conch "0.8.0"]
                                  [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT" :exclusions [com.dadrox/quiet-slf4j
                                                                                         com.google.code.findbugs/jsr305
                                                                                         gorilla-repl
                                                                                         org.slf4j/slf4j-nop]]
                                  [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT" :exclusions [commons-io]]
                                  [org.clojure/tools.namespace "0.2.11"]]
                   :source-paths ["dev-resources/src"
                                  "libs/common-lib/src"
                                  "libs/transmit-lib/src"]
                   :repl-options {:init-ns cmr.dev.env.manager.repl
                                  :prompt #(str "\u001B[35m[\u001B[34m"
                                                %
                                                "\u001B[35m]\u001B[33m λ\u001B[m=> ")}}
             :custom-repl {:repl-options {}}
             ;:welcome ~(print-welcome)
             :test {:plugins [[lein-ancient "0.6.14"]
                              [jonase/eastwood "0.2.5"]
                              [lein-bikeshed "0.5.0"]
                              [lein-kibit "0.1.8"]]}
             :lint {:source-paths ^:replace ["src"]}
             :docs {:dependencies [[clojang/codox-theme "0.2.0-SNAPSHOT"]]
                    :plugins [[lein-codox "0.10.3"]
                              [lein-simpleton "1.3.0"]]
                    :codox {:project {:name "CMR D.E.M."}
                            :themes [:clojang]
                            :output-path "docs/current"
                            :doc-paths ["resources/docs"]
                            :namespaces [#"^cmr\.dev\.env\.manager\.(?!test)"]
                            :metadata {:doc/format :markdown}}}
             :instrumented {:jvm-opts ["-Dcom.sun.management.jmxremote"
                                       "-Dcom.sun.management.jmxremote.ssl=false"
                                       "-Dcom.sun.management.jmxremote.authenticate=false"
                                       "-Dcom.sun.management.jmxremote.port=43210"]}
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
    ;;;   Profiles for Managed Aapplications/Services   ;;;;;;;;;;;;;;;;;;;;;
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
    ;; Note that CMR service port configuration is currently pulled in from
    ;; the `cmr.transmit.config` ns; see `cmr.dev.env.manager.config`.
             :access-control {:main cmr.access-control.runner
                              :source-paths ["apps/access-control-app/src"
                                             "apps/metadata-db-app/src"
                                             "libs/acl-lib/src"
                                             "libs/common-app-lib/src"
                                             "libs/common-lib/src"
                                             "libs/elastic-utils-lib/src"
                                             "libs/message-queue-lib/src"
                                             "libs/transmit-lib/src"
                                             "libs/umm-spec-lib/src"]}
             :bootstrap {:main cmr.bootstrap.runner
                         :source-paths ["apps/access-control-app/src"
                                        "apps/bootstrap-app/src"
                                        "apps/indexer-app/src"
                                        "apps/metadata-db-app/src"
                                        "apps/virtual-product-app/src"
                                        "libs/common-app-lib/src"
                                        "libs/oracle-lib/src"
                                        "libs/transmit-lib/src"]}
             :indexer {:main cmr.indexer.runner
                       :source-paths ["apps/indexer-app/src"
                                      "libs/acl-lib/src"
                                      "libs/common-app-lib/src"
                                      "libs/elastic-utils-lib/src"
                                      "libs/message-queue-lib/src"
                                      "libs/transmit-lib/src"
                                      "libs/umm-lib/src"
                                      "libs/umm-spec-lib/src"]}
             :ingest {:main cmr.ingest.runner
                      :source-paths ["apps/ingest-app/src"
                                     "libs/acl-lib/src"
                                     "libs/common-app-lib/src"
                                     "libs/message-queue-lib/src"
                                     "libs/oracle-lib/src"
                                     "libs/transmit-lib/src"
                                     "libs/umm-lib/src"
                                     "libs/umm-spec-lib/src"]}
             :metadata-db {:main cmr.metadata-db.runner
                           :source-paths ["apps/metadata-db-app/src"
                                          "libs/acl-lib/src"
                                          "libs/common-app-lib/src"
                                          "libs/common-lib/src"
                                          "libs/message-queue-lib/src"
                                          "libs/oracle-lib/src"]}
             :mock-echo {:autoreload true
                         :main cmr.mock-echo.runner
                         :source-paths ["apps/mock-echo-app/src"
                                        "libs/common-app-lib/src"
                                        "libs/common-lib/src"
                                        "libs/transmit-lib/src"]}
             :search {:main cmr.search.runner
                      :source-paths ["apps/search-app/src"
                                     "libs/common-app-lib/src"
                                     "libs/elastic-utils-lib/src"
                                     "libs/message-queue-lib/src"
                                     "libs/orbits-lib/src"
                                     "libs/spatial-lib/src"
                                     "libs/umm-lib/src"
                                     "libs/umm-spec-lib/src"]}
             :virtual-product {:main cmr.virtual-product.runner
                               :source-paths ["apps/virtual-product-app/src"
                                              "libs/common-app-lib/src"
                                              "libs/common-lib/src"
                                              "libs/message-queue-lib/src"
                                              "libs/transmit-lib/src"
                                              "libs/umm-lib/src"]}}
  :aliases {
            ;; General aliases
            "repl" ["trampoline" "repl"]
            "unprotected-repl" ["repl"]
            "ubercompile" ["with-profile" "+ubercompile" "do"
                           ["clean"]
                           ["compile"]
                           ["clean"]]
            "check-deps" ["with-profile" "+test" "ancient" "check" ":all"]
            "lint" ["with-profile" "+test,+lint" "kibit"]
            "docs" ["with-profile" "+docs" "do"
                    ["clean"]
                    ["compile"]
                    ["codox"]
                    ["clean"]]
            "build" ["with-profile" "+test" "do"
                     ["check-deps"]
                     ["lint"]
                     ["docs"]
                     ["ubercompile"]
                     ["clean"]
                     ["uberjar"]
                     ["clean"]
                     ["test"]]
            ;; Profiling
            "instrumented-repl" ["with-profile" "+instrumented" "repl"]
            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;;;   Application Aliases   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            "mock-echo" ["trampoline" "with-profile" "+dev,+mock-echo" "run"]})
