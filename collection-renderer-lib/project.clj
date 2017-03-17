(def jruby-version
  "The version of JRuby to use. This is the same as used in the echo orbits java package to prevent
   classpath issues"
  "9.1.8.0")

(def gem-install-path
  "The directory within this library where Ruby gems are installed."
  "gems")

(defproject nasa-cmr/cmr-collection-renderer-lib "0.1.0-SNAPSHOT"
  :description "Renders collections as HTML"
  :url "***REMOVED***projects/CMR/repos/cmr/browse/collection-renderer-lib"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-umm-spec-lib "0.1.0-SNAPSHOT"]
                 [org.jruby/jruby-complete ~jruby-version]]
  :plugins [[test2junit "1.2.1"]
            [lein-shell "0.4.0"]]
  :resource-paths ["resources" ~gem-install-path]
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                        [proto-repl "0.3.1"]
                        [pjstadig/humane-test-output "0.8.1"]]
         :jvm-opts ^:replace ["-server"]
         :source-paths ["src" "dev" "test"]
         :injections [(require 'pjstadig.humane-test-output)
                      (pjstadig.humane-test-output/activate!)]}}
  :aliases {"install-gems" ["shell"
                            "support/install_gems.sh"
                            "***REMOVED***scm/cmr/cmr_metadata_preview.git"]
            "clean-gems" ["shell" "rm" "-rf" ~gem-install-path]
            ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]})
