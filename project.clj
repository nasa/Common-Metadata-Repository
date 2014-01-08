(defproject nasa-cmr/cmr-vdd-spatial-viz "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [element84/vdd-core "0.1.1-SNAPSHOT"]
                 [clj-coffee-script "1.1.0"]]

  ;; TODO
  ;; refactor stuff in driver
  ;; remove the src directory (or put stuff in it)
  :source-paths ["viz" "src"]

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]

         :source-paths ["src" "dev" "viz"]}})
