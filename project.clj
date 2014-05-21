(defproject nasa-cmr/cmr-system-int-test "0.1.0-SNAPSHOT"
  :description "This project provides end to end integration testing for CMR components."
  :url "***REMOVED***projects/CMR/repos/cmr-system-int-test/browse"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  ;; Add sonatype repository to pull in the 0.0.8-SNAPSHOT of org.clojure/data.xml
  ;; We need this snapshot version of data.xml to address this issue: http://dev.clojure.org/jira/browse/DXML-14
  ;; Once data.xml 0.0.8 is officially released, we should remove this repository
  :repositories [["snapshots" "https://oss.sonatype.org/content/repositories/snapshots/"]]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-http "0.9.1"]
                 [org.clojure/data.xml "0.0.8-SNAPSHOT"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"]

                 ;; included for access to messages
                 [nasa-cmr/cmr-search-app "0.1.0-SNAPSHOT"]

                 ; include ring-core to support encoding of params
                 [ring/ring-core "1.2.2"]
                 [cheshire "5.3.1"]]
  :plugins [[lein-test-out "0.3.1"]]

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]
                        [nasa-cmr/cmr-vdd-spatial-viz "0.1.0-SNAPSHOT"]]
         :source-paths ["src" "dev"]}})
