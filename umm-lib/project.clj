(defproject nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"
  :description "The UMM (Unified Metadata Model) Library is responsible for defining the common domain
               model for Metadata Concepts in the CMR along with code to parse and generate the
               various dialects of each concept."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/umm-lib"
  :exclusions [
    [org.clojure/clojure]
    [org.clojure/tools.reader]]
  :dependencies [
    [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
    [nasa-cmr/cmr-spatial-lib "0.1.0-SNAPSHOT"]
    [org.clojure/clojure "1.8.0"]
    [org.clojure/tools.reader "1.1.1"]]
  :plugins [
    [lein-shell "0.5.0"]
    [test2junit "1.3.3"]]
  ;; The ^replace is done to disable the tiered compilation for accurate benchmarks
  ;; See https://github.com/technomancy/leiningen/wiki/Faster
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {
    :dev {
      :exclusions [
        [org.clojure/tools.nrepl]]
      :dependencies [
        [criterium "0.4.4"]
        [org.clojars.gjahad/debug-repl "0.3.3"]
        [org.clojure/tools.namespace "0.2.11"]
        [org.clojure/tools.nrepl "0.2.13"]
        [pjstadig/humane-test-output "0.8.3"]
        [proto-repl "0.3.1"]]
      :jvm-opts ^:replace ["-server"]
                           ;; Uncomment this to enable assertions. Turn off during performance tests.
                           ; "-ea"

                           ;; Use the following to enable JMX profiling with visualvm
                           ;; "-Dcom.sun.management.jmxremote"
                           ;; "-Dcom.sun.management.jmxremote.ssl=false"
                           ;; "-Dcom.sun.management.jmxremote.authenticate=false"
                           ;; "-Dcom.sun.management.jmxremote.port=1098"]
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
  :aliases {;; Alias to test2junit for consistency with lein-test-out
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
