(defn get-banner
  []
  (try
    (str
      (slurp "resources/text/banner.txt"))
      ;(slurp "resources/text/loading.txt")

    ;; If another project can't find the banner, just skip it;
    ;; this function is really only meant to be used by Dragon itself.
    (catch Exception _ "")))

(defn get-prompt
  [ns]
  (str "\u001B[35m[\u001B[34m"
       ns
       "\u001B[35m]\u001B[33m λ\u001B[m=> "))

(defproject cmr-graph "0.1.0-SNAPSHOT"
  :description "A service and API for querying CMR metadata relationships"
  :url "https://github.com/cmr-exchange/cmr-graph"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[cheshire "5.8.0"]
                 [clojurewerkz/elastisch "3.0.0"]
                 [clojurewerkz/neocons "3.2.0"]
                 [clojusc/system-manager "0.3.0"]
                 [clojusc/trifl "0.4.0"]
                 [clojusc/twig "0.3.2"]
                 [com.stuartsierra/component "0.3.2"]
                 [digest "1.4.6"]
                 [gov.nasa.earthdata/cmr-authz "0.1.3"]
                 [gov.nasa.earthdata/cmr-exchange-common "0.3.3"]
                 [gov.nasa.earthdata/cmr-http-kit "0.2.0"]
                 [gov.nasa.earthdata/cmr-process-manager "0.1.1-SNAPSHOT"]
                 [http-kit "2.2.0"]
                 [metosin/reitit-core "0.3.9"]
                 [metosin/reitit-ring "0.3.9"]
                 [metosin/ring-http-response "0.9.1"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/data.csv "0.1.4"]
                 [ring/ring-core "1.6.3"]
                 [ring/ring-codec "1.1.0"]
                 [ring/ring-defaults "0.3.1"]]
  :main cmr.graph.core
  :profiles {:ubercompile {:aot :all}
             :dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [proto-repl "0.3.1"]]
                   :plugins [[lein-shell "0.5.0"]
                             [venantius/ultra "0.5.2"]]
                   :source-paths ["dev-resources/src"]
                   :repl-options {:init-ns cmr.graph.repl
                                  :prompt ~get-prompt
                                  :init ~(println (get-banner))}}
             :lint {:source-paths ^:replace ["src"]
                    :test-paths ^:replace []
                    :plugins [[jonase/eastwood "0.2.5"]
                              [lein-ancient "0.6.15"]
                              [lein-bikeshed "0.5.1"]
                              [lein-kibit "0.1.8"]]}
             :test {:dependencies [[clojusc/ltest "0.3.0"]]
                    :plugins [[lein-ltest "0.3.0"]
                              [test2junit "1.4.0"]]
                    :test2junit-output-dir "junit-test-results"
                    :test-selectors {:unit #(not (or (:integration %) (:system %)))
                                     :integration :integration
                                     :system :system
                                     :default (complement :system)}}
             :docs {:dependencies [[clojang/codox-theme "0.2.0-SNAPSHOT"]]
                    :plugins [[lein-codox "0.10.3"]
                              [lein-marginalia "0.9.1"]]
                    :codox {:project {:name "CMR Graph"
                                      :description "A service and API for querying CMR metadata relationships"}
                            :namespaces [#"^cmr\.graph\.(?!dev)"]
                            :metadata {:doc/format :markdown
                                       :doc "Documentation forthcoming"}
                            :themes [:clojang]
                            :doc-paths ["resources/docs"]
                            :output-path "docs/current"}}}
  :aliases {
            ;; Dev & Testing Aliases
            "repl" ["do"
                    ["clean"]
                    ["repl"]]
            "ubercompile" ["with-profile" "+ubercompile" "compile"]
            "check-vers" ["with-profile" "+lint" "ancient" "check" ":all"]
            "check-jars" ["with-profile" "+lint" "do"
                          ["deps" ":tree"]
                          ["deps" ":plugin-tree"]]
            "check-deps" ["do"
                          ["check-jars"]
                          ["check-vers"]]
            "kibit" ["with-profile" "+lint" "kibit"]
            "eastwood" ["with-profile" "+lint" "eastwood" "{:namespaces [:source-paths]}"]
            "lint" ["do"
                    ["kibit"]]
                    ;["eastwood"]

            "ltest" ["with-profile" "+test" "ltest"]
            "junit" ["with-profile" "+test" "test2junit"]
            ;; Documentation
            "codox" ["with-profile" "+docs"
                     "codox"]
            "marginalia" ["with-profile" "+docs"
                          "marg" "--dir" "docs/current"
                          "--file" "marginalia.html"
                          "--name" "Clojure Protocol Buffer Library"]
            "docs" ["do"
                    ["codox"]
                    ["marginalia"]]
            ;; Build tasks
            "build" ["do"
                     ["ltest" ":unit"]
                     ["junit" ":unit"]
                     ["ubercompile"]
                     ["uberjar"]]
            ;; Application
            "start-cmr-graph"
            ["trampoline" "run"]
            ;; Docker Aliases
            "docker-clean" [
                            "shell"
                            "docker" "system" "prune" "-f"]
            "start-infra" [
                           "shell"
                           "docker-compose" "-f" "resources/docker/docker-compose.yml" "up"]
            "stop-infra" [
                          "shell"
                          "docker-compose" "-f" "resources/docker/docker-compose.yml" "down"]})
