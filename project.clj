(defproject cmr-system-int-test "0.1.0-SNAPSHOT"
  :description "This project provides end to end integration testing for CMR components."
  :url "***REMOVED***projects/CMR/repos/cmr-system-int-test/browse"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clj-http "0.9.0"]
                 [org.clojure/data.xml "0.0.7"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 ; include ring-core to support encoding of params
                 [ring/ring-core "1.2.2"]
                 [cheshire "5.2.0"]]

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :source-paths ["src" "dev"]}})
