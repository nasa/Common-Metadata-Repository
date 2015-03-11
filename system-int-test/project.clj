(defproject nasa-cmr/cmr-system-int-test "0.1.0-SNAPSHOT"
  :description "This project provides end to end integration testing for CMR components."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/system-int-test"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-http "1.0.1"]
                 [nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-elastic-utils-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]

                 ;; included for access to messages
                 [nasa-cmr/cmr-search-app "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-ingest-app "0.1.0-SNAPSHOT"]

                 ;; included to allow access to catalog rest and db connection code
                 [nasa-cmr/cmr-bootstrap-app "0.1.0-SNAPSHOT"]

                 ; include ring-core to support encoding of params
                 [ring/ring-core "1.3.2" :exclusions [clj-time]]]
  :plugins [[lein-test-out "0.3.1"]]

  :jvm-opts ["-XX:PermSize=256m" "-XX:MaxPermSize=256m" "-XX:-OmitStackTraceInFastThrow"]

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.10"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]
                        [nasa-cmr/cmr-vdd-spatial-viz "0.1.0-SNAPSHOT"]]
         :source-paths ["src" "dev"]}})
