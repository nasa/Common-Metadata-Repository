(defn get-prompt
  [ns]
  (str "\u001B[35m[\u001B[34m"
       ns
       "\u001B[35m]\u001B[33m λ\u001B[m=> "))

(defproject gov.nasa.earthdata/cmr-edsc-stubs "0.2.0-SNAPSHOT"
  :description "Various Stubbed Data for CMR / EDSC"
  :url "https://github.com/oubiwann/cmr-edsc-stubs"
  :license {:name "Apache License 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[cheshire "5.8.0"]
                 [clj-time "0.14.0"]
                 [clojusc/trifl "0.1.0"]
                 [gov.nasa.earthdata/cmr-client "0.3.0-SNAPSHOT"]
                 [gov.nasa.earthdata/cmr-sample-data "0.2.0-SNAPSHOT"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/java.jdbc "0.7.1"]
                 [potemkin "0.4.4"]]
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]]
                   :source-paths ["dev-resources/src"]
                   :repl-options {:init-ns cmr-edsc-stubs.dev
                                  :prompt ~get-prompt}}})
