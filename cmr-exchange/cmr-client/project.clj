(defproject gov.nasa.earthdata/cmr-client "0.3.0-SNAPSHOT"
  :description "A Clojure(Script) Client for NASA's Common Metadata Repository"
  :url "https://github.com/cmr-exchange/cmr-client"
  :license {
            :name "Apache License, Version 2.0"
            :url "https://opensource.org/licenses/Apache-2.0"}
  :exclusions [org.clojure/clojure
               potemkin]
  :dependencies [[clj-http "3.8.0"]
                 [cljs-http "0.1.44"]
                 [clojusc/ltest "0.3.0"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.217"]
                 [org.clojure/core.async "0.4.474"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/data.xml "0.2.0-alpha2"]
                 [potemkin "0.4.4"]]
  :source-paths ["src/clj" "src/cljc"]
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[leiningen-core "2.8.1"]
                                  [org.clojure/tools.namespace "0.2.11"]]
                   :plugins [[lein-cljsbuild "1.1.7"]
                             [lein-figwheel "0.5.15"]
                             [lein-shell "0.5.0"]]
                   :resource-paths ["dev-resources" "test/data" "test/clj"]
                   :source-paths ["src/clj" "src/cljc" "test/clj" "dev-resources/src"]
                   :test-paths ["test/clj"]
                   :repl-options {:init-ns cmr.client.dev}}
             :test {:resource-paths ["test/data"]
                    :source-paths ["test/clj"]
                    :test-paths ["test/clj"]
                    :test-selectors {:default :unit
                                     :unit :unit
                                     :integration :integration
                                     :system :system}}
             :lint {:exclusions ^:replace [org.clojure/clojure
                                           org.clojure/tools.namespace]
                    :dependencies ^:replace [[org.clojure/clojure "1.9.0"]
                                             [org.clojure/tools.namespace "0.2.11"]]
                    :source-paths ^:replace ["src/clj" "src/cljc"]
                    :test-paths ^:replace []
                    :plugins [[jonase/eastwood "0.2.5"]
                              [lein-ancient "0.6.15"]
                              [lein-bikeshed "0.5.1"]
                              [lein-kibit "0.1.8"]]}
             :cljs {:source-paths ^:replace ["src/cljs" "src/cljc"]}
             :docs {:dependencies [[clojang/codox-theme "0.2.0-SNAPSHOT"]]
                    :plugins [[lein-codox "0.10.3"]
                              [lein-marginalia "0.9.1"]
                              [lein-simpleton "1.3.0"]]
                    :codox {:project {:name "CMR Client"
                                      :description "A Clojure(Script)+JavaScript Client for NASA's Common Metadata Repository"}
                            :namespaces [#"^cmr\.client\..*"]
                            :metadata {:doc/format :markdown
                                       :doc "Documentation forthcoming"}
                            :themes [:clojang]
                            :doc-paths ["resources/docs"]
                            :output-path "docs/current"}}}
  :cljsbuild {:builds [{:id "cmr-dev"
                        :source-paths ["src/cljs" "src/cljc"]
                        :figwheel true
                        :compiler {:main "cmr.client"
                                   :asset-path "js/out"
                                   :output-to "resources/public/js/cmr_dev.js"
                                   :output-dir "resources/public/js/out"}}
                       {:id "cmr-prod"
                        :source-paths ["src/cljs" "src/cljc"]
                        :compiler {:main "cmr.client"
                                   :static-fns true
                                   :fn-invoke-direct true
                                   :optimizations :simple
                                   :output-to "resources/public/js/cmr_client.js"}}]}
  :aliases {"repl"
            ["with-profile" "+dev" "repl"]
            "build-cljs-dev"
            ^{:doc "Build just the dev version of the ClojureScript code"}
            ["cljsbuild" "once" "cmr-dev"]
            "build-cljs-prod"
            ^{:doc "Build just the prod version of the ClojureScript code"}
            ["do"
              ["shell" "rm" "-f" "resources/public/js/cmr_client.js"]
              ["cljsbuild" "once" "cmr-prod"]]
            "run-tests"
            ^{:doc "Use the ltest runner for verbose, colourful test output"}
            ["with-profile" "+test"
             "run" "-m" "cmr.client.testing.runner"]
            "check-vers" ["with-profile" "+lint" "ancient" "check" ":all"]
            "check-jars" ["with-profile" "+lint" "do"
                          ["deps" ":tree"]
                          ["deps" ":plugin-tree"]]
            "check-deps"
            ^{:doc "Check to see if any dependencies are out of date or if jars are conflicting"}
            ["do"
              ["check-jars"]
              ["check-vers"]]
            "lint"
            ^{:doc "Run linting tools against the source"}
            ["with-profile" "+lint" "kibit"]
            "docs"
            ^{:doc "Generate API documentation"}
            ["with-profile" "+docs" "do"
              ["codox"]]
            ; ["marg" "--dir" "docs/current"
            ;         "--file" "marginalia.html"
            ;         "--name" "sockets"]
            ;["shell" "cp" "resources/public/cdn.html" "docs"]

            "build"
            ^{:doc "Perform the build tasks"}
            ["with-profile" "+test" "do"
            ;["check-deps"]
              ["lint"]
              ["test"]
              ["compile"]
              ["docs"]
              ["uberjar"]
              ["build-cljs-dev"]
              ["build-cljs-prod"]]
            "npm"
            ^{:doc "Publish compiled JavaScript client"}
            ["do"
              ["shell" "rm" "-rf" "dist"]
              ["shell" "mkdir" "dist"]
              ["shell" "cp" "resources/public/js/cmr_client.js" "dist"]
              ["shell" "npm" "publish" "--access" "public"]
              ["shell" "rm" "-rf" "dist"]]
            "publish"
            ^{:doc "Publish to Clojars and npm"}
            ["do"
              ["deploy" "clojars"]
              ["npm"]]})
