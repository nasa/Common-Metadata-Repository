(defproject nasa-cmr/cmr-vdd-spatial-viz "0.1.0-SNAPSHOT"
  :description "A visualization tool for spatial areas."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/vdd-spatial-viz"
  :exclusions [
    [org.clojure/clojurescript]
    [org.clojure/core.incubator]]
  :dependencies [
    [clj-coffee-script "1.1.0"]
    [element84/vdd-core "0.1.2"]
    [org.clojure/clojure "1.8.0"]
    [org.clojure/clojurescript "1.9.946"]
    [org.clojure/core.incubator "0.1.2"]]
  :source-paths ["viz" "src"]

  :plugins [[lein-exec "0.3.2"]
            [lein-shell "0.4.0"]
            [test2junit "1.2.1"]]

  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {
    :dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                         [org.clojars.gjahad/debug-repl "0.3.3"]]
          :jvm-opts ^:replace ["-server"]
          :source-paths ["src" "dev" "viz"]}
    :static {}
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
  ;; Must be manually run before running lein install
  :aliases {"compile-coffeescript" ["exec" "-ep" "(common-viz.util/compile-coffeescript (vdd-core.core/config))"]
            ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]
            ;; Linting aliases
            "kibit" ["do" ["with-profile" "lint" "shell" "echo" "== Kibit =="]
                          ["with-profile" "lint" "kibit"]]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "yagni" ["with-profile" "lint" "yagni"]
            "check-deps" ["with-profile" "lint" "ancient" "all"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]
            ;; Placeholder for future docs and enabler of top-level alias
            "generate-static" ["with-profile" "static" "shell" "echo"]})
