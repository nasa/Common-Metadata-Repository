(defproject nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"
  :description "Provides common utility code for CMR projects."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/common-lib"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.385"]
                 [org.ow2.asm/asm "5.1"]

                 [com.taoensso/timbre "4.1.4"]

                 [org.clojure/test.check "0.9.0"]
                 [com.gfredericks/test.chuck "0.2.7"]
                 [org.clojure/data.xml "0.0.8"]
                 [camel-snake-kebab "0.4.0"]

                 ;; Note that we copied some code from this library into in memory cache. Replace that when updating.
                 [org.clojure/core.cache "0.6.5"]
                 [org.clojure/data.codec "0.1.0"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [clojurewerkz/quartzite "2.0.0"]
                 [clj-time "0.12.0"]
                 [cheshire "5.6.3"]
                 [environ "1.1.0"]
                 ;; Fast compression library
                 [net.jpountz.lz4/lz4 "1.3.0"]

                 [ring/ring-jetty-adapter "1.5.0"]
                 ;; Needed for GzipHandler
                 ;; Matches the version of Jetty used by ring-jetty-adapter
                 [org.eclipse.jetty/jetty-servlets "9.2.10.v20150310"]

                 ;; Needed for timeout a function execution
                 [clojail "1.0.6"]
                 [com.github.fge/json-schema-validator "2.2.6"]
                 [com.dadrox/quiet-slf4j "0.1"]

                 [compojure "1.5.1"]
                 [ring/ring-json "0.4.0"]
                 ;; Excludes things that are specified with other parts of the CMR
                 [gorilla-repl "0.3.6" :exclusions [org.clojure/java.classpath
                                                    ch.qos.logback/logback-classic
                                                    javax.servlet/servlet-api
                                                    compojure
                                                    ring-json]]]

  :plugins [[lein-exec "0.3.2"]
            [lein-shell "0.4.0"]
            [test2junit "1.2.1"]]
  :global-vars {*warn-on-reflection* true}

  ;; The ^replace is done to disable the tiered compilation for accurate benchmarks
  ;; See https://github.com/technomancy/leiningen/wiki/Faster
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {
    :dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                         [org.clojars.gjahad/debug-repl "0.3.3"]
                         [criterium "0.4.4"]
                         [proto-repl "0.3.1"]
                         [clj-http "2.0.0"]]
          :jvm-opts ^:replace ["-server"]
                               ;; XXX Note that profiling can be kept in a profile,
                               ;;     with no need to comment/uncomment.
                               ;; Uncomment this to enable assertions. Turn off during performance tests.
                               ; "-ea"

                               ;; Use the following to enable JMX profiling with visualvm
                               ; "-Dcom.sun.management.jmxremote"
                               ; "-Dcom.sun.management.jmxremote.ssl=false"
                               ; "-Dcom.sun.management.jmxremote.authenticate=false"
                               ; "-Dcom.sun.management.jmxremote.port=1098"]
          :source-paths ["src" "dev" "test"]}
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
  :aliases {;; Alias to test2junit for consistency with lein-test-out
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
