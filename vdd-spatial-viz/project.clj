(defproject nasa-cmr/cmr-vdd-spatial-viz "0.1.0-SNAPSHOT"
  :description "A visualization tool for spatial areas."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/vdd-spatial-viz"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [element84/vdd-core "0.1.2"]
                 [clj-coffee-script "1.1.0"]]

  :source-paths ["viz" "src"]

  :plugins [[lein-exec "0.3.2"]
            [lein-modules "0.3.11"]
            [lein-test-out "0.3.1"]]

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.10"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]

         :source-paths ["src" "dev" "viz"]}}

  ;; Must be manually run before running lein install
  :aliases {"compile-coffeescript" ["exec" "-ep" "(common-viz.util/compile-coffeescript (vdd-core.core/config))"]})
