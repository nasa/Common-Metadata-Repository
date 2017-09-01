(defproject gov.nasa.earthdata/cmr-client "0.1.0-SNAPSHOT"
  :description "A Clojure(Script) Client for NASA's Common Metadata Repository"
  :url "https://github.com/oubiwann/cmr-client"
  :license {
    :name "Apache License, Version 2.0"
    :url "https://opensource.org/licenses/Apache-2.0"}
  :dependencies [
    [clj-http "3.7.0"]
    [cljs-http "0.1.43"]
    [org.clojure/clojure "1.8.0"]
    [org.clojure/clojurescript "1.9.908"]
    [org.clojure/core.async "0.3.443"]
    [org.clojure/data.json "0.2.6"]
    [org.clojure/data.xml "0.2.0-alpha2"]
    [potemkin "0.4.4"]]
  :source-paths ["src/clj" "src/cljc"]
  :profiles {
    :uberjar {
      :aot :all}
    :dev {
      :dependencies [
        [org.clojure/tools.namespace "0.2.11"]]
      :plugins [
        [lein-cljsbuild "1.1.7" :exclusions [org.clojure/clojure]]
        [lein-figwheel "0.5.13" :exclusions [org.clojure/clojure]]]
      :resource-paths ["dev-resources"]
      :source-paths ["dev-resources/src"]
      :repl-options {
        :init-ns cmr.client.dev}}
    :test {
      :dependencies [
        [clojusc/ltest "0.2.0-SNAPSHOT"]]
      :source-paths ["test/clj"]
    }
    :lint {
      :source-paths ^:replace ["src"]
      :test-paths ^:replace []
      :plugins [
        [jonase/eastwood "0.2.3"]
        [lein-ancient "0.6.10"]
        [lein-bikeshed "0.4.1"]
        [lein-kibit "0.1.2"]
        [venantius/yagni "0.1.4"]]}
    :cljs {
      :source-paths ^:replace ["src/cljs" "src/cljc"]}
    :docs {
      :dependencies [
        [codox-theme-rdash "0.1.2"]]
      :plugins [
        [lein-codox "0.10.3"]
        [lein-marginalia "0.9.0"]
        [lein-simpleton "1.3.0"]]
      :codox {
        :project {
          :name "CMR Client"
          :description "A Clojure(Script) Client for NASA's Common Metadata Repository"}
        :namespaces [#"^cmr\.(?!dev)"]
        :themes [:rdash]
        :output-path "docs/current"
        :doc-paths ["resources/docs"]
        :metadata {
          :doc/format :markdown
          :doc "Documentation forthcoming"}}}}
  :cljsbuild {
    :builds [
      {:id "cmr-dev"
       :source-paths ["src/cljs" "src/cljc"]
       :figwheel true
       :compiler {
         :main "cmr.client"
         :asset-path "js/out"
         :output-to "resources/public/js/cmr_dev.js"
         :output-dir "resources/public/js/out"}}
      {:id "cmr-prod"
       :source-paths ["src/cljs" "src/cljc"]
       :compiler {
         :main "cmr.client"
         :static-fns true
         :fn-invoke-direct true
         :optimizations :simple
         :output-to "resources/public/js/cmr_client.js"}}]}
  :aliases {
    "build-cljs-dev"
      ^{:doc "Build just the dev version of the ClojureScript code."}
      ["cljsbuild" "once" "cmr-dev"]
    "build-cljs-prod"
      ^{:doc "Build just the prod version of the ClojureScript code."}
      ["cljsbuild" "once" "cmr-prod"]
    "run-tests"
      ^{:doc "Use the ltest runner for verbose, colourful test output"}
      ["with-profile" "+test"
       "run" "-m" "cmr.client.testing.runner"]
    "check-deps"
      ^{:doc "Check to see if any dependencies are out of date"}
      ["with-profile" "lint" "ancient" "all"]
    "lint" ["with-profile" "+test" "kibit"]
    "docs" ["with-profile" "+docs" "do"
      ["codox"]
      ; ["marg" "--dir" "docs/current"
      ;         "--file" "marginalia.html"
      ;         "--name" "sockets"]
      ]
    "build" ["with-profile" "+test" "do"
      ["check-deps"]
      ["lint"]
      ["test"]
      ["compile"]
      ["docs"]
      ["uberjar"]]})
