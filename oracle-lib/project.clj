(def oracle-jar-repo-env-var
  "The name of an environment variable that when set indicates an internal maven repo containing
   the Oracle JDBC jars. Set this environment variable to avoid having to manually download
   Oracle JDBC jars."
  "CMR_ORACLE_JAR_REPO")

(def extra-repository
  "The set of repositories to include if configured"
  (if-let [repo (get (System/getenv) oracle-jar-repo-env-var)]
    repo
    "http://example.com/no_url_specified_this_is_ignored"))

(defproject nasa-cmr/cmr-oracle-lib "0.1.0-SNAPSHOT"
  :description "Contains utilities for connecting to and manipulating data in Oracle."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/oracle-lib"
  ;; Dynamically include extra repositories in the project definition if configured.
  :repositories [["releases" ~extra-repository]]
  :dependencies [
    [com.oracle/ojdbc6 "11.2.0.4"]
    [com.oracle/ons "11.2.0.4"]
    [com.oracle/ucp "11.2.0.4"]
    [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
    [org.clojure/clojure "1.8.0"]
    [org.clojure/java.jdbc "0.4.2"]
    [sqlingvo "0.7.15"]]
  :plugins [
    [lein-shell "0.5.0"]
    [test2junit "1.3.3"]]
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {
    :dev {
      :exclusions [
        [org.clojure/tools.nrepl]]
      :dependencies [
        [org.clojars.gjahad/debug-repl "0.3.3"]
        [org.clojure/tools.namespace "0.2.11"]
        [org.clojure/tools.nrepl "0.2.13"]]
      :jvm-opts ^:replace ["-server"]
      :source-paths ["src" "dev" "test"]}
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
            ;; Eastwood needs special handling with libs that include oracle
            ;; drivers in the deps, in particulear:
            ;;   java.lang.ClassNotFoundException: oracle.dms.console.DMSConsole
            "eastwood" ["with-profile" "lint" "eastwood"
                        "{:namespaces [:source-paths] :exclude-namespaces [cmr.oracle.connection]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "yagni" ["with-profile" "lint" "yagni"]
            "check-deps" ["with-profile" "lint" "ancient" ":all"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]
            ;; Placeholder for future docs and enabler of top-level alias
            "generate-static" ["with-profile" "static" "shell" "echo"]})
