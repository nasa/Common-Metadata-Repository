(defproject nasa-cmr/index-queue-app "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-system-trace-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
                 [compojure "1.1.9"]
                 [ring/ring-core "1.3.1" :exclusions [clj-time]]
                 [ring/ring-json "0.3.1"]
                 [org.clojure/tools.reader "0.8.8"]
                 [org.clojure/tools.cli "0.3.1"]
                 [com.novemberain/langohr "3.0.1"]]
  :plugins [[lein-test-out "0.3.1"]]
  :repl-options {:init-ns user}
  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.5"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :source-paths ["src" "dev" "test"]}
   :uberjar {:main cmr.index-queue.runner
             :aot :all}})


