(defproject nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"
  :description "Provides common utility code for CMR projects."
  :url "***REMOVED***projects/CMR/repos/cmr-common-lib/browse"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.taoensso/timbre "3.2.0"]
                 [ring/ring-jetty-adapter "1.2.2"]
                 [org.clojure/test.check "0.5.8"]
                 [org.clojure/data.xml "0.0.7"]
                 [camel-snake-kebab "0.1.5"]
                 [org.clojure/core.cache "0.6.3"]
                 [org.clojure/data.codec "0.1.0"]
                 [com.novemberain/pantomime "2.1.0"]

                 ;; Needed for GzipHandler
                 [org.eclipse.jetty/jetty-servlets "7.6.8.v20121106"]]

   :plugins [[lein-test-out "0.3.1"]]

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :source-paths ["src" "dev" "test"]}})


