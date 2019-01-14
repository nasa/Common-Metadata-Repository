(defproject nasa-cmr/cmr-umm-spec-lib "0.1.0-SNAPSHOT"
  :description "Defines the Unified Metadata Model and mappings from various metadata standards into UMM."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/umm-spec-lib"
  :exclusions [
    [commons-io]
    [org.apache.httpcomponents/httpcore]
    [org.clojure/clojure]
    [org.clojure/tools.reader]]
  :dependencies [
    [commons-io "2.6"]
    [commons-validator/commons-validator "1.5.1"]
    [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
    [nasa-cmr/cmr-spatial-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"]
    [org.apache.httpcomponents/httpcore "4.4.10"]
    [org.clojure/clojure "1.10.0"]
    [org.clojure/tools.reader "1.3.2"]]
  :plugins [
    [lein-exec "0.3.7"]
    [lein-shell "0.5.0"]
    [test2junit "1.3.3"]]
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {
    :security {
      :plugins [
        [com.livingsocial/lein-dependency-check "1.1.0"]]}
    :dev {
      :exclusions [
        [org.clojure/tools.nrepl]]
      :dependencies [
        [clj-http "2.3.0"]
        [criterium "0.4.4"]
        [org.clojars.gjahad/debug-repl "0.3.3"]
        [org.clojure/tools.namespace "0.2.11"]
        [org.clojure/tools.nrepl "0.2.13"]
        [pjstadig/humane-test-output "0.9.0"]
        [proto-repl "0.3.1"]]
      ;; The ^replace is done to disable the tiered compilation for accurate benchmarks
      ;; See https://github.com/technomancy/leiningen/wiki/Faster
      :jvm-opts ^:replace ["-server"]
      ;                      ;; Use the following to enable JMX profiling with visualvm
      ;                      "-Dcom.sun.management.jmxremote"
      ;                      "-Dcom.sun.management.jmxremote.ssl=false"
      ;                      "-Dcom.sun.management.jmxremote.authenticate=false"
      ;                      "-Dcom.sun.management.jmxremote.port=1098"]
      :source-paths ["src" "dev" "test"]
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
  :aliases {"generate-umm-records" ["exec" "-ep" "(do (use 'cmr.umm-spec.record-generator) (generate-umm-records))"]
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
