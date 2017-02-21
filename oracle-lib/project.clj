
(def oracle-jar-repo-env-var
  "The name of an environment variable that when set indicates an internal maven repo containing
   the Oracle JDBC jars. Set this environment variable to avoid having to manually download
   Oracle JDBC jars."
  "CMR_ORACLE_JAR_REPO")

(def extra-repositories
  "The set of repositories to include if configured"
  (when-let [repo (get (System/getenv) oracle-jar-repo-env-var)]
    [["releases" repo]]))

(defproject nasa-cmr/cmr-oracle-lib "0.1.0-SNAPSHOT"
  :description "Contains utilities for connecting to and manipulating data in Oracle."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/oracle-lib"

  ;; Dynamically include extra repositories in the project definition if configured.
  :repositories ~extra-repositories

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
  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]]
         :jvm-opts ^:replace ["-server"]
         :source-paths ["src" "dev" "test"]}}

  :aliases { ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]})
