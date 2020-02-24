(defproject nasa-cmr/cmr-elastic-utils-lib "0.1.0-SNAPSHOT"
  :description "A library containing utilities for dealing with Elasticsearch."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/elastic-utils-lib"
  :exclusions [[cheshire]
               [commons-codec/commons-codec]
               [commons-io]
               [org.elasticsearch/elasticsearch]
               [potemkin]]
  :dependencies [[cheshire "5.8.1"]
                 [clj-http "2.3.0"]
                 [clojurewerkz/elastisch "5.0.0-beta1"]
                 [commons-codec/commons-codec "1.11"]
                 [commons-io "2.6"]
                 [log4j/log4j "1.2.17"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [org.clojure/clojure "1.10.0"]
                 [org.codelibs/elasticsearch-cluster-runner "7.5.2.0"]
                 [org.elasticsearch/elasticsearch "7.5.2"]
                 [potemkin "0.4.5"]]
  :plugins [[lein-shell "0.5.0"]
            [test2junit "1.3.3"]]
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.1.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"}}
             :dev {:exclusions [[org.clojure/tools.nrepl]]
                   :dependencies [[org.clojars.gjahad/debug-repl "0.3.3"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/tools.nrepl "0.2.13"]]
                   :jvm-opts ^:replace ["-server"]
                   :source-paths ["src" "dev" "test"]}
             :static {}
             ;; This profile is used for linting and static analysis. To run for this
             ;; project, use `lein lint` from inside the project directory. To run for
             ;; all projects at the same time, use the same command but from the top-
             ;; level directory.
             :lint {:source-paths ^:replace ["src"]
                    :test-paths ^:replace []
                    :plugins [[jonase/eastwood "0.2.5"]
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
            "check-sec" ["with-profile" "security" "dependency-check"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]
            ;; Placeholder for future docs and enabler of top-level alias
            "generate-static" ["with-profile" "static" "shell" "echo"]})
