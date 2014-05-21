(defproject nasa-cmr/cmr-vdd-spatial-viz "0.1.0-SNAPSHOT"
  :description "A visualization tool for spatial areas."
  :url "***REMOVED***projects/CMR/repos/cmr-vdd-spatial-viz/browse"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [element84/vdd-core "0.1.1"]
                 [clj-coffee-script "1.1.0"]]

  ;; TODO
  ;; refactor stuff in driver
  ;; remove the src directory (or put stuff in it)
  :source-paths ["viz" "src"]

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]

         :source-paths ["src" "dev" "viz"]}})
