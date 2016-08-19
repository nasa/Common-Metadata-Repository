(defproject nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"
  :description "Provides common utility code for CMR projects."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/common-lib"

  :dependencies [[org.clojure/clojure "1.8.0"]

                 ;; ASM is excluded here because we use the pegdown markdown generator in common app lib which uses a different version
                 [org.clojure/core.async "0.1.346.0-17112a-alpha" :exclusions [org.ow2.asm/asm-all]]
                 ;; This matches the ASM version used by pegdown at the time of this writing
                 [org.ow2.asm/asm "5.0.3"]

                 [com.taoensso/timbre "4.1.4"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 ;; Needed for parsing accept header
                 [ring-middleware-format "0.6.0"]
                 [org.clojure/test.check "0.8.2"]
                 [com.gfredericks/test.chuck "0.2.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [camel-snake-kebab "0.3.2" :exclusions [org.clojure/clojure]]
                 [org.clojure/core.cache "0.6.4"]
                 [org.clojure/data.codec "0.1.0"]
                 [org.clojure/tools.nrepl "0.2.11"]
                 [clojurewerkz/quartzite "2.0.0"]
                 [clj-time "0.11.0"]
                 [cheshire "5.5.0"]

                 ;; Fast compression library
                 [net.jpountz.lz4/lz4 "1.3.0"]

                 ;; Needed for GzipHandler
                 ;; Matches the version of Jetty used by ring-jetty-adapter
                 [org.eclipse.jetty/jetty-servlets "9.2.10.v20150310"]

                 ;; Needed for timeout a function execution
                 [clojail "1.0.6"]
                 [com.github.fge/json-schema-validator "2.2.6"]
                 [com.dadrox/quiet-slf4j "0.1"]

                 [compojure "1.4.0"]
                 [ring/ring-json "0.4.0"]
                 ;; Excludes things that are specified with other parts of the CMR
                 ;; 0.3.5-SNAPSHOT is used because it gives us the ability to clear the output.
                 [gorilla-repl "0.3.5-SNAPSHOT"  :exclusions [org.clojure/java.classpath
                                                              ch.qos.logback/logback-classic
                                                              javax.servlet/servlet-api
                                                              compojure
                                                              ring-json]]]

  :plugins [[test2junit "1.2.1"]
            [lein-exec "0.3.2"]]

  :global-vars {*warn-on-reflection* true}

  ;; The ^replace is done to disable the tiered compilation for accurate benchmarks
  ;; See https://github.com/technomancy/leiningen/wiki/Faster
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                        [org.clojars.gjahad/debug-repl "0.3.3"]
                        [criterium "0.4.3"]
                        [proto-repl "0.3.1"]
                        [clj-http "2.0.0"]]
         :jvm-opts ^:replace ["-server"]
                               ;; Uncomment this to enable assertions. Turn off during performance tests.
                               ; "-ea"

                               ;; Use the following to enable JMX profiling with visualvm
                               ; "-Dcom.sun.management.jmxremote"
                               ; "-Dcom.sun.management.jmxremote.ssl=false"
                               ; "-Dcom.sun.management.jmxremote.authenticate=false"
                               ; "-Dcom.sun.management.jmxremote.port=1098"]
         :source-paths ["src" "dev" "test"]}}
  :aliases {;; Alias to test2junit for consistency with lein-test-out
             "test-out" ["test2junit"]})
