(defproject nasa-cmr/cmr-system-int-test "0.1.0-SNAPSHOT"
  :description "This project provides end to end integration testing for CMR components."
  :url "***REMOVED***browse/system-int-test"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-http "2.0.0"]
                 [nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-umm-spec-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-elastic-utils-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-umm-spec-lib "0.1.0-SNAPSHOT"]

                 ;; included for access to messages or setting config
                 [nasa-cmr/cmr-search-app "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-ingest-app "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-indexer-app "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-virtual-product-app "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-access-control-app "0.1.0-SNAPSHOT"]

                 ;; included to allow access to catalog rest and db connection code
                 [nasa-cmr/cmr-bootstrap-app "0.1.0-SNAPSHOT"]

                 ;; Needed for client libraries
                 [nasa-cmr/cmr-mock-echo-app "0.1.0-SNAPSHOT"]

                 ; include ring-core to support encoding of params
                 [ring/ring-core "1.5.0"]

                 ;; Needed for ring-swagger dependency in search for as long as we provide the
                 ;; swagger-ui as part of search (until the developer portal is available)
                 [prismatic/schema "1.1.3"]]

  :plugins [[test2junit "1.2.1"]]

  :jvm-opts ^:replace ["-server"
                       "-XX:-OmitStackTraceInFastThrow"
                       "-Dclojure.compiler.direct-linking=true"]

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]
                        [nasa-cmr/cmr-vdd-spatial-viz "0.1.0-SNAPSHOT"]
                        [pjstadig/humane-test-output "0.8.1"]]
         :injections [(require 'pjstadig.humane-test-output)
                      (pjstadig.humane-test-output/activate!)]
         :jvm-opts ^:replace ["-server"
                              "-XX:-OmitStackTraceInFastThrow"]
         :source-paths ["src" "dev"]}}
  :aliases { ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]})
