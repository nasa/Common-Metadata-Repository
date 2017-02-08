(def jruby-version
  "The version of JRuby to use. This is the same as used in the echo orbits java package to prevent
   classpath issues"
  "1.7.4")

(def jruby-jar-path
  "The path to the JRuby Jar to use when installing gems"
  (format "%s/.m2/repository/org/jruby/jruby-complete/%s/jruby-complete-%s.jar"
          (get (System/getProperties) "user.home")
          jruby-version jruby-version))

(defproject orbits-lib "0.1.0-SNAPSHOT"
  :description "TODO write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [org.jruby/jruby-complete ~jruby-version]]
  :plugins [[test2junit "1.2.1"]
            [lein-shell "0.4.0"]]

  :resource-paths ["resources"]
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
  :aliases {;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]})
