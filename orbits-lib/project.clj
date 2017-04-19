(def jruby-version
  "The version of JRuby to use. This is the same as used in the echo orbits java package to prevent
   classpath issues"
  "9.1.8.0")

(def jruby-jar-path
  "The path to the JRuby Jar to use when installing gems"
  (format "%s/.m2/repository/org/jruby/jruby-complete/%s/jruby-complete-%s.jar"
          (get (System/getProperties) "user.home")
          jruby-version jruby-version))

(defn install-gem-command
  "Returns a shell command that will install the given version of the gem"
  [gem-dir gem-name version]
  ["shell" "echo" "Installing" gem-name (str version ",")
   "shell"
   ;; Run JRuby
   "java" "-cp" jruby-jar-path "org.jruby.Main" "-S"
   ;; Install a specific version of the gem in a given location
   "gem" "install" "-i" gem-dir gem-name "-v"
   ;; comma appended to last command so shell commands can be strung together with do
   (str version ",")])

(defn install-gems-command
  "Takes a list of tuples of gem names and versions. Returns the set of shell commands to install
   the given gems"
  [gem-dir gem-versions]
  (reduce into ["do"] (for [[gem-name version] gem-versions]
                        (install-gem-command gem-dir gem-name version))))

(def dev-gem-install-path
  "The directory within this library where Ruby gems are installed for development time dependencies."
  "dev-gems")

(def dev-gem-versions
  "The list of development time dependency gems and their versions to install"
  [["rspec" "2.12.0"]])

(defproject nasa-cmr/cmr-orbits-lib "0.1.0-SNAPSHOT"
  :description "Contains Ruby code that allows performing orbit calculations for spatial search."
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [org.jruby/jruby-complete ~jruby-version]]
  :plugins [[test2junit "1.2.1"]
            [lein-shell "0.4.0"]]

  :resource-paths ["resources"]
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {
    :dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                         [proto-repl "0.3.1"]
                         [pjstadig/humane-test-output "0.8.1"]]
          :jvm-opts ^:replace ["-server"]
          :source-paths ["src" "dev" "test"]
          :resource-paths ["resources" "test_resources" ~dev-gem-install-path]
          :injections [(require 'pjstadig.humane-test-output)
                       (pjstadig.humane-test-output/activate!)]}
    ;; This profile is used for linting and static analysis. To run for this
    ;; project, use `lein lint` from inside the project directory. To run for
    ;; all projects at the same time, use the same command but from the top-
    ;; level directory.
    :lint {
      :source-paths ^:replace ["src"]
      :test-paths ^:replace []
      :plugins [[jonase/eastwood "0.2.3"]
                [lein-ancient "0.6.10"]
                [lein-bikeshed "0.4.1"]
                [lein-kibit "0.1.2"]
                [lein-shell "0.4.0"]
                [venantius/yagni "0.1.4"]]}}
  :aliases {"install-gems" ~(install-gems-command dev-gem-install-path dev-gem-versions)
            "clean-gems" ["shell" "rm" "-rf" ~dev-gem-install-path]
            ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]
            ;; Linting aliases
            "kibit" ["do" ["with-profile" "lint" "shell" "echo" "== Kibit =="]
                          ["with-profile" "lint" "kibit"]]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "yagni" ["with-profile" "lint" "yagni"]
            "check-deps" ["with-profile" "lint" "ancient"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]})
