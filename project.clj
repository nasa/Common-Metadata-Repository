(defproject gov.nasa.earthdata/cmr-client "0.2.0-SNAPSHOT"
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
        [lein-figwheel "0.5.13" :exclusions [org.clojure/clojure]]
        [lein-shell "0.5.0"]]
      :resource-paths ["dev-resources"]
      :source-paths ["dev-resources/src"]
      :repl-options {
        :init-ns cmr.client.dev}}
    :test {
      :dependencies [
        ;[clojusc/ltest "0.2.0-SNAPSHOT"]
        ]
      :source-paths ["test/clj"]
    }
    :lint {
      :source-paths ^:replace ["src"]
      :test-paths ^:replace []
      :plugins [
        [jonase/eastwood "0.2.4"]
        [lein-ancient "0.6.10"]
        [lein-bikeshed "0.4.1"]
        [lein-kibit "0.1.5"]
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
          :description "A Clojure(Script)+JavaScript Client for NASA's Common Metadata Repository"}
        :namespaces [#"^cmr\.client\..*"]
        :metadata {
          :doc/format :markdown
          :doc "Documentation forthcoming"}
        :themes [:rdash]
        :doc-paths ["docs/md"]
        :output-path "docs/current"}}}
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
    "repl"
      ["with-profile" "+dev,+test" "repl"]
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
    "check-deps"
      ^{:doc "Check to see if any dependencies are out of date"}
      ["with-profile" "lint" "ancient" "all"]
    "lint"
      ^{:doc "Run linting tools against the source"}
      ["with-profile" "+test" "kibit"]
    "docs"
      ^{:doc "Generate API documentation"}
      ["with-profile" "+docs" "do"
        ["codox"]
        ; ["marg" "--dir" "docs/current"
        ;         "--file" "marginalia.html"
        ;         "--name" "sockets"]
        ["shell" "cp" "resources/public/cdn.html" "docs"]]
    "build"
      ^{:doc "Perform the build tasks"}
      ["with-profile" "+test" "do"
        ["check-deps"]
        ;["lint"]
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
