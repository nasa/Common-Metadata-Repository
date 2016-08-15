(defproject nasa-cmr/cmr-vdd-spatial-viz "0.1.0-SNAPSHOT"
  :description "A visualization tool for spatial areas."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/vdd-spatial-viz"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [element84/vdd-core "0.1.2"]
                 [clj-coffee-script "1.1.0"]]

  :source-paths ["viz" "src"]

  :plugins [[lein-exec "0.3.2"]
            [test2junit "1.2.1"]]

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]

         :source-paths ["src" "dev" "viz"]}}

  ;; Must be manually run before running lein install
  :aliases {"compile-coffeescript" ["exec" "-ep" "(common-viz.util/compile-coffeescript (vdd-core.core/config))"]
            ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]})
