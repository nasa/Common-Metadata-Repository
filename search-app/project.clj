(defproject nasa-cmr/cmr-search-app "0.1.0-SNAPSHOT"
  :description "Provides a public search API for concepts in the CMR."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/search-app"
  :exclusions [
    [cheshire]
    [clj-time]
    [org.clojure/clojure]
    [org.clojure/tools.reader]]
  :dependencies [
    [cheshire "5.8.0"]
    [clj-time "0.14.2"]
    [com.github.fge/json-schema-validator "2.2.6"]
    ;; XXX REMOVE the following deps when the stubbed
    ;;     responses are replaced with the real ones
    ;;     See https://bugs.earthdata.nasa.gov/browse/CMR-4583
    [clojusc/ltest "0.3.0-SNAPSHOT"]
    [gov.nasa.earthdata/cmr-client "0.2.0-SNAPSHOT"
     :exclusions [cljs-http
                  clj-http
                  clojusc/ltest
                  com.google.code.findbugs/jsr305
                  gov.nasa.earthdata/cmr-client
                  instaparse
                  org.clojure/clojurescript
                  org.clojure/core.async
                  org.clojure/data.xml
                  org.clojure/tools.analyzer.jvm
                  org.clojure/tools.reader
                  org.clojure/java.jdbc
                  ring/ring-codec]]
    [gov.nasa.earthdata/cmr-edsc-stubs "0.2.0-SNAPSHOT"
     :exclusions [cljs-http
                  clj-http
                  clojusc/ltest
                  com.google.code.findbugs/jsr305
                  gov.nasa.earthdata/cmr-client
                  instaparse
                  org.clojure/clojurescript
                  org.clojure/core.async
                  org.clojure/data.xml
                  org.clojure/tools.analyzer.jvm
                  org.clojure/tools.reader
                  org.clojure/java.jdbc
                  ring/ring-codec]]
    ;; XXX end REMOVE
    [nasa-cmr/cmr-collection-renderer-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
    [nasa-cmr/cmr-elastic-utils-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-metadata-db-app "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-orbits-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-spatial-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-umm-spec-lib "0.1.0-SNAPSHOT"]
    [net.sf.saxon/Saxon-HE "9.7.0-7"]
    [org.clojure/clojure "1.8.0"]
    [org.clojure/data.csv "0.1.3"]
    [org.clojure/tools.nrepl "0.2.12"]
    [org.clojure/tools.reader "1.1.1"]
    [ring/ring-core "1.5.0"]
    [ring/ring-json "0.4.0"]
    [selmer "1.10.7"]
    ;; Temporary inclusion of libraries needed for swagger UI until the dev portal is
    ;; done.
    [metosin/ring-swagger-ui "2.1.4-0"]
    [metosin/ring-swagger "0.22.9"]
    [prismatic/schema "1.1.3"]]

  :plugins [[test2junit "1.2.1"]
            [lein-exec "0.3.4"]]
  :repl-options {:init-ns user
                 :timeout 120000}
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {
    :dev {:dependencies [[ring-mock "0.1.5"]
                         [org.clojure/tools.namespace "0.2.11"]
                         [org.clojars.gjahad/debug-repl "0.3.3"]
                         [criterium "0.4.4"]
                         [pjstadig/humane-test-output "0.8.1"]
                         ;; Must be listed here as metadata db depends on it.
                         [drift "1.5.3"]]
          :jvm-opts ^:replace ["-server"]
          :source-paths ["src" "dev" "test"]
          :injections [(require 'pjstadig.humane-test-output)
                       (pjstadig.humane-test-output/activate!)]}

    ;; This profile specifically here for generating documentation. It's
    ;; faster than using the regular profile. An agent pool is being started
    ;; when using the default profile which causes the wait of 60 seconds
    ;; before allowing the JVM to shutdown since no call to shutdown-agents is
    ;; made. Generate docs with: lein generate-static (the alias makes use of the
    ;; static profile).
    :static {}

    :uberjar {:main cmr.search.runner
              :aot :all}

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

  :aliases {"generate-static" ["with-profile" "static"
                               "run" "-m" "cmr.search.site.static" "all"]
            ;; Prints out documentation on configuration environment variables.
            "env-config-docs" ["exec" "-ep" "(do (use 'cmr.common.config) (print-all-configs-docs) (shutdown-agents))"]
            ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]
            ;; Linting aliases
            "kibit" ["do" ["with-profile" "lint" "shell" "echo" "== Kibit =="]
                          ["with-profile" "lint" "kibit"]]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "yagni" ["with-profile" "lint" "yagni"]
            "check-deps" ["with-profile" "lint" "ancient" "all"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]})
