(defn get-prompt
  [ns]
  (str "\u001B[35m[\u001B[34m"
       ns
       "\u001B[35m]\u001B[33m λ\u001B[m=> "))

(defproject gov.nasa.earthdata/cmr-sample-data "0.2.0-SNAPSHOT"
  :description "Sample Data for the open source NASA Common Metadata Repository (CMR)"
  :url "https://github.com/oubiwann/cmr-sample-data"
  :license {:name "Apache License 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"}
  :exclusions [org.clojure/clojure]
  :dependencies [[cheshire "5.8.0"]
                 [org.clojure/clojure "1.9.0"]]
  :profiles {:ubercompile {:aot :all}
             :dev {:dependencies [[clojusc/trifl "0.2.0"]
                                  [org.clojure/tools.namespace "0.2.11"]]
                   :source-paths ["dev-resources/src"]
                   :repl-options {:init-ns cmr.sample-data.dev
                                  :prompt ~get-prompt}}
             :test {:plugins [[lein-ancient "0.6.15"]
                              [jonase/eastwood "0.2.5"]
                              [lein-bikeshed "0.5.0"]
                              [lein-kibit "0.1.8"]
                              [venantius/yagni "0.1.4"]]}}
  :aliases {"ubercompile" ["with-profile" "+ubercompile" "compile"]
            "check-deps" ["with-profile" "+test" "ancient" "check" ":all"]
            "kibit" ["with-profile" "+test" "kibit"]
            "eastwood" ["with-profile" "+test" "eastwood" "{:namespaces [:source-paths]}"]
            "lint" ["with-profile" "+test" "do"
                    ["kibit"]
                    ["eastwood"]]
            "build" ["with-profile" "+test" "do"
                     ["check-deps"]
                     ["lint"]
                     ["ubercompile"]
                     ["clean"]
                     ["uberjar"]
                     ["clean"]
                     ["test"]]})
