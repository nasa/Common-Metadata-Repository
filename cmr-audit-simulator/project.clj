(defproject nasa-cmr/cmr-audit-simulator "0.1.0-SNAPSHOT"
  :description "A deliberately flawed CMR-style app used to test audit tooling."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/cmr-audit-simulator"
  :dependencies [[org.clojure/clojure "1.11.2"]
                 [cheshire "5.12.0"]
                 [clj-http "3.11.0"]]
  :plugins [[lein-shell "0.5.0"]]
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {:dev {:source-paths ["src" "dev" "test"]}
             :uberjar {:main cmr.audit-simulator.runner
                       :aot :all}
             :lint {:source-paths ^:replace ["src"]
                    :test-paths ^:replace []
                    :plugins [[jonase/eastwood "1.4.2"]
                              [lein-ancient "0.7.0"]
                              [lein-bikeshed "0.5.0"]
                              [lein-kibit "0.1.6"]]}}
  :aliases {"run-simulation" ["run" "-m" "cmr.audit-simulator.runner"]
            "kibit" ["with-profile" "lint" "kibit"]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "check-deps" ["with-profile" "lint" "ancient" ":all"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]})
