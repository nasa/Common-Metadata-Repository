(defproject nasa-cmr/cmr-umm-spec-lib "0.1.0-SNAPSHOT"
  :description "Defines the Unified Metadata Model and mappings from various metadata standards into UMM."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/umm-spec-lib"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-spatial-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"]
                 [commons-validator/commons-validator "1.5.0"]]

  :plugins [[lein-exec "0.3.2"]
            [lein-shell "0.4.0"]
            [test2junit "1.2.1"]]
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {
    :dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                         [org.clojars.gjahad/debug-repl "0.3.3"]
                         [pjstadig/humane-test-output "0.8.1"]
                         [criterium "0.4.4"]
                         [proto-repl "0.3.1"]
                         [clj-http "2.0.0"]]

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
    :docs {}
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
                [venantius/yagni "0.1.4"]]}}
  :aliases {"generate-umm-records" ["exec" "-ep" "(do (use 'cmr.umm-spec.record-generator) (generate-umm-records))"]
            ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]
            ;; Linting aliases
            "kibit" ["do" ["with-profile" "lint" "shell" "echo" "== Kibit =="]
                          ["with-profile" "lint" "kibit"]]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "yagni" ["with-profile" "lint" "yagni"]
            "check-deps" ["with-profile" "lint" "ancient"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]
            ;; Placeholder for future docs and enabler of top-level alias
            "generate-docs" ["with-profile" "docs" "shell" "echo"]})
