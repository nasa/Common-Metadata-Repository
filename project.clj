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
    [org.clojure/data.xml "0.2.0-alpha2"]]
  :source-paths ["src/clj" "src/cljc"]
  :profiles {
    :dev {
      :dependencies [
        [org.clojure/tools.namespace "0.2.11"]]
      :plugins [
        [lein-cljsbuild "1.1.7"]
        [lein-figwheel "0.5.13"]]
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
      :source-paths ^:replace ["src/cljs" "src/cljc"]
      }}
  :cljsbuild {
    :builds [{
      :id "cmr-client"
      :source-paths ["src/cljs" "src/cljc"]
      :figwheel true
      :compiler {
        :main "cmr.client.ingest"
        :asset-path "js/out"
        :output-to "resources/public/js/cmr_client.js"
        :output-dir "resources/public/js/out"}}]}
  :aliases {
    "build-cljs"
      ^{:doc "Build just the ClojureScript code."}
      ["cljsbuild" "once" "cmr-client"]
    "run-tests"
      ^{:doc "Use the ltest runner for verbose, colourful test output"}
      ["with-profile" "+test"
       "run" "-m" "cmr.client.testing.runner"]
    "check-deps"
      ^{:doc "Check to see if any dependencies are out of date"}
      ["with-profile" "lint" "ancient" "all"]})
