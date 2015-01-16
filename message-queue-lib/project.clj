(defproject nasa-cmr/cmr-message-queue-lib "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-system-trace-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
                 [com.novemberain/langohr "3.0.1"]
                 [com.taoensso/timbre "3.3.1"]
                 [ring/ring-jetty-adapter "1.3.1"]
                 [org.clojure/test.check "0.5.9"]
                 [org.clojure/data.xml "0.0.8"]
                 [camel-snake-kebab "0.1.5"]
                 [org.clojure/core.cache "0.6.4"]
                 [org.clojure/data.codec "0.1.0"]
                 [com.novemberain/pantomime "2.3.0"]
                 [clojurewerkz/quartzite "1.3.0"]
                 [clj-time "0.8.0"]

                 ;; Needed for GzipHandler
                 [org.eclipse.jetty/jetty-servlets "7.6.8.v20121106"]
                 ;; Needed for timeout a function execution
                 [clojail "1.0.6"]
                 ;; Used to compute ttls
                 [org.clojure/math.numeric-tower "0.0.4"]]
  :plugins [[lein-test-out "0.3.1"]]

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.5"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :source-paths ["src" "dev" "test"]}})
