(def jruby-version
  "The version of JRuby to use. This is the same as used in the echo orbits java package to prevent
   classpath issues"
  "9.1.8.0")

(def jruby-jar-path
  "The path to the JRuby Jar to use when installing gems"
  (format "%s/.m2/repository/org/jruby/jruby-complete/%s/jruby-complete-%s.jar"
          (get (System/getProperties) "user.home")
          jruby-version jruby-version))

(def gem-install-path
  "The directory within this library where Ruby gems are installed."
  "gems")

(defn install-gem-command
  "Returns a shell command that will install the given version of the gem"
  [gem-name version]
  ["shell" "echo" "Installing" gem-name (str version ",")
   "shell"
   ;; Run JRuby
   "java" "-cp" jruby-jar-path "org.jruby.Main" "-S"
   ;; Install a specific version of the gem in a given location
   "gem" "install" "-i" gem-install-path gem-name "-v"
   ;; comma appended to last command so shell commands can be strung together with do
   (str version ",")])

(defn install-gems-command
  "Takes a list of tuples of gem names and versions. Returns the set of shell commands to install
   the given gems"
  [gem-versions]
  (reduce into ["do"] (for [[gem-name version] gem-versions]
                        (install-gem-command gem-name version))))

(def rails-version
  "The version of Rails used in MMT. This is used when installing gems to get the same version."
  "4.2.5.1")

(def gem-versions
  "The list of gems and their versions to install"
  [["activesupport" rails-version]
   ["actionview" rails-version]
   ["actionpack" rails-version]])

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
                            "https://git@git.earthdata.nasa.gov/scm/cmr/cmr_metadata_preview.git"]
            "clean-gems" ["shell" "rm" "-rf" ~gem-install-path]
            ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]})
