(def jruby-version
  "The version of JRuby to use. This is the same as used in the echo orbits java package to prevent
   classpath issues"
  "9.1.8.0")

(def dev-gem-install-path
  "The directory within this library where Ruby gems are installed for development time dependencies."
  "dev-gems")

(defproject nasa-cmr/cmr-orbits-lib "0.1.0-SNAPSHOT"
  :description "Contains Ruby code that allows performing orbit calculations for spatial search."
  :dependencies [
    [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
    [org.clojure/clojure "1.8.0"]
    [org.jruby/jruby-complete ~jruby-version]]
  :plugins [
    [lein-shell "0.5.0"]
    [test2junit "1.3.3"]]
  :resource-paths ["resources"]
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {
    :dev {
      :exclusions [
        [org.clojure/tools.nrepl]]
      :dependencies [
        [org.clojure/tools.namespace "0.2.11"]
        [org.clojure/tools.nrepl "0.2.13"]
        [pjstadig/humane-test-output "0.8.3"]
        [proto-repl "0.3.1"]]
      :jvm-opts ^:replace ["-server"]
      :source-paths ["src" "dev" "test"]
      :resource-paths ["resources" "test_resources" ~dev-gem-install-path]
      :injections [(require 'pjstadig.humane-test-output)
                   (pjstadig.humane-test-output/activate!)]}
    :static {}
    ;; This profile is used for linting and static analysis. To run for this
    ;; project, use `lein lint` from inside the project directory. To run for
    ;; all projects at the same time, use the same command but from the top-
    ;; level directory.
    :lint {
      :source-paths ^:replace ["src"]
      :test-paths ^:replace []
      :plugins [
        [jonase/eastwood "0.2.5"]
        [lein-ancient "0.6.15"]
        [lein-bikeshed "0.5.0"]
        [lein-kibit "0.1.6"]
        [venantius/yagni "0.1.4"]]}
    ;; The following profile is overriden on the build server or in the user's
    ;; ~/.lein/profiles.clj file.
    :internal-repos {}}
  :aliases {"install-gems" ["shell"
                            "support/install_gems.sh"
                            ~jruby-version
                            ~dev-gem-install-path]
            "clean-gems" ["shell" "rm" "-rf" ~dev-gem-install-path]
            ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]
            ;; Linting aliases
            "kibit" ["do" ["with-profile" "lint" "shell" "echo" "== Kibit =="]
                          ["with-profile" "lint" "kibit"]]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "yagni" ["with-profile" "lint" "yagni"]
            "check-deps" ["with-profile" "lint" "ancient" ":all"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]
            ;; Placeholder for future docs and enabler of top-level alias
            "generate-static" ["with-profile" "static" "shell" "echo"]})
