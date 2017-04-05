
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
  :url "***REMOVED***browse/oracle-lib"

  ;; Dynamically include extra repositories in the project definition if configured.
  :repositories [["releases" ~extra-repository]]

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [sqlingvo "0.7.15"]

                 ;; These Oracle JDBC Driver jars are not available via public maven repositories.
                 ;; They have to manually installed in the local repo. See README for instructions.
                 [com.oracle/ojdbc6 "11.2.0.4"]
                 [com.oracle/ons "11.2.0.4"]
                 [com.oracle/ucp "11.2.0.4"]]

  :plugins [[test2junit "1.2.1"]]
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {
    :dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                         [org.clojars.gjahad/debug-repl "0.3.3"]]
          :jvm-opts ^:replace ["-server"]
          :source-paths ["src" "dev" "test"]}
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
            "bikeshed" ["with-profile" "lint" "bikeshed"]
            "yagni" ["with-profile" "lint" "yagni"]
            "check-deps" ["with-profile" "lint" "ancient"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]})
