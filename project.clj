(defproject nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"
  :description "The UMM (Unified Metadata Model) Library is responsible for defining the common domain
               model for Metadata Concepts in the CMR along with code to parse and generate the
               various dialects of each concept."
  :url "***REMOVED***projects/CMR/repos/cmr-umm-lib/browse"

  ;; Add sonatype repository to pull in the 0.0.8-SNAPSHOT of org.clojure/data.xml
  ;; We need this snapshot version of data.xml to address this issue: http://dev.clojure.org/jira/browse/DXML-14
  ;; Once data.xml 0.0.8 is officially released, we should remove this repository
  :repositories [["snapshots" "https://oss.sonatype.org/content/repositories/snapshots/"]]

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/test.check "0.5.7"]
                 [org.clojure/data.xml "0.0.8-SNAPSHOT"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [camel-snake-kebab "0.1.5"]]

  :plugins [[lein-test-out "0.3.1"]]

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]

         :source-paths ["src" "dev" "test"]}})


