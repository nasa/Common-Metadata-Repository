(defproject nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"
  :description "Provides common utility code for CMR projects."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/common-lib"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.taoensso/timbre "3.4.0"]
                 [ring/ring-jetty-adapter "1.3.2"]
                 [org.clojure/test.check "0.7.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [camel-snake-kebab "0.3.0" :exclusions [org.clojure/clojure]]
                 [org.clojure/core.cache "0.6.4"]
                 [org.clojure/data.codec "0.1.0"]
                 [com.novemberain/pantomime "2.4.0"]
                 [clojurewerkz/quartzite "2.0.0"]
                 [clj-time "0.9.0"]
                 [cheshire "5.4.0"]

                 ;; Needed for GzipHandler
                 [org.eclipse.jetty/jetty-servlets "7.6.8.v20121106"]
                 ;; Needed for timeout a function execution
                 [clojail "1.0.6"]]

   :plugins [[lein-test-out "0.3.1"]
             [lein-exec "0.3.2"]]

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.9"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :source-paths ["src" "dev" "test"]}})


