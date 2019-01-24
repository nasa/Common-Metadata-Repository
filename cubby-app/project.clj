(defproject nasa-cmr/cmr-cubby-app "0.1.0-SNAPSHOT"
  :description "Provides a centralized caching service for the CMR. See README for details."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/cubby-app"
  :exclusions [
    [cheshire]
    [clj-time]
    [org.clojure/tools.reader]]
  :dependencies [
    [cheshire "5.8.1"]
    [clj-time "0.15.1"]
    [compojure "1.6.1"]
    [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
    [nasa-cmr/cmr-elastic-utils-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
    [org.clojure/clojure "1.10.0"]
    [org.clojure/tools.reader "1.3.2"]
    [ring/ring-core "1.7.1"]
    [ring/ring-json "0.4.0"]]
  :plugins [
    [lein-exec "0.3.7"]
    [lein-shell "0.5.0"]
    [test2junit "1.3.3"]]
  :repl-options {:init-ns user}
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :test-paths ["int-test"]
  :profiles {
    :security {
      :plugins [
        [com.livingsocial/lein-dependency-check "1.1.1"]]
      :dependency-check {
        :output-format [:all]
        :suppression-file "resources/security/suppression.xml"}}
    :dev {
      :exclusions [
        [org.clojure/tools.nrepl]]
      :dependencies [
        [nasa-cmr/cmr-mock-echo-app "0.1.0-SNAPSHOT"]
        [org.clojars.gjahad/debug-repl "0.3.3"]
        [org.clojure/tools.namespace "0.2.11"]
        [org.clojure/tools.nrepl "0.2.13"]
        [pjstadig/humane-test-output "0.9.0"]]
      :jvm-opts ^:replace ["-server"]
      :source-paths ["src" "dev" "test" "int-test"]
      :injections [(require 'pjstadig.humane-test-output)
                   (pjstadig.humane-test-output/activate!)]}
    :uberjar {:main cmr.cubby.runner
              :aot :all}
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
  :aliases {;; Prints out documentation on configuration environment variables.
            "env-config-docs" ["exec" "-ep" "(do (use 'cmr.common.config) (print-all-configs-docs) (shutdown-agents))"]
            ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]
            ;; Linting aliases
            "kibit" ["do" ["with-profile" "lint" "shell" "echo" "== Kibit =="]
                          ["with-profile" "lint" "kibit"]]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "yagni" ["with-profile" "lint" "yagni"]
            "check-deps" ["with-profile" "lint" "ancient" ":all"]
            "check-sec" ["with-profile" "security" "dependency-check"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]
            ;; Placeholder for future docs and enabler of top-level alias
            "generate-static" ["with-profile" "static" "shell" "echo"]})
