(defproject nasa-cmr/cmr-search-app "0.1.0-SNAPSHOT"
  :description "Provides a public search API for concepts in the CMR."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/search-app"
  :exclusions [
    [cheshire]
    [clj-time]
    [com.fasterxml.jackson.core/jackson-core]
    [commons-codec/commons-codec]
    [org.apache.httpcomponents/httpclient]
    [org.clojure/clojure]
    [org.clojure/tools.reader]
    [ring/ring-codec]]
  :dependencies [
    [cheshire "5.8.0"]
    [clj-time "0.14.2"]
    [com.fasterxml.jackson.core/jackson-core "2.9.3"]
    [com.github.fge/json-schema-validator "2.2.6"]
    [commons-codec/commons-codec "1.11"]
    [nasa-cmr/cmr-collection-renderer-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
    [nasa-cmr/cmr-elastic-utils-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-metadata-db-app "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-orbits-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-spatial-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-umm-spec-lib "0.1.0-SNAPSHOT"]
    [net.sf.saxon/Saxon-HE "9.8.0-7"]
    [org.apache.httpcomponents/httpclient "4.5.4"]
    [org.clojure/clojure "1.8.0"]
    [org.clojure/data.csv "0.1.4"]
    [org.clojure/tools.reader "1.1.1"]
    [ring/ring-codec "1.1.0"]
    [ring/ring-core "1.6.3"]
    [ring/ring-json "0.4.0"]
    [selmer "1.11.5"]
    ;; Temporary inclusion of libraries needed for swagger UI until the dev portal is
    ;; done.
    [metosin/ring-swagger-ui "2.1.4-0"]
    [metosin/ring-swagger "0.22.14"]
    [prismatic/schema "1.1.7"]]
  :plugins [
    [lein-exec "0.3.7"]
    [test2junit "1.3.3"]]
  :repl-options {:init-ns user
                 :timeout 120000}
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {
    :dev {
      :exclusions [
        [org.clojure/tools.nrepl]]
      :dependencies [
        [criterium "0.4.4"]
        [drift "1.5.3"]
        [org.clojars.gjahad/debug-repl "0.3.3"]
        [org.clojure/tools.namespace "0.2.11"]
        [org.clojure/tools.nrepl "0.2.13"]
        [pjstadig/humane-test-output "0.8.3"]
        [ring-mock "0.1.5"]]
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
      :plugins [
        [jonase/eastwood "0.2.5"]
        [lein-ancient "0.6.15"]
        [lein-bikeshed "0.5.0"]
        [lein-kibit "0.1.6"]
        [lein-shell "0.5.0"]
        [venantius/yagni "0.1.4"]]}
    ;; The following profile is overriden on the build server or in the user's
    ;; ~/.lein/profiles.clj file.
    :internal-repos {}}
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
            "check-deps" ["with-profile" "lint" "ancient" ":all"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]})
