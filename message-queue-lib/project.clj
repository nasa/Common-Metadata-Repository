(defproject nasa-cmr/cmr-message-queue-lib "0.1.0-SNAPSHOT"
  :description "Library containing code to handle message queue interactions within the CMR."
  :url "***REMOVED***projects/CMR/repos/cmr/browse/message-queue-lib"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-acl-lib "0.1.0-SNAPSHOT"]
                 [com.novemberain/langohr "3.4.0"]
                 [com.amazonaws/aws-java-sdk "1.10.60"]]

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
