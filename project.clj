(defproject gov.nasa.earthdata/cmr-client "0.1.0-SNAPSHOT"
  :description "A Clojure(Script) Client for NASA's Common Metadata Repository"
  :url "https://github.com/oubiwann/cmr-client"
  :license {:name "Apache License, Version 2.0"
            :url "https://opensource.org/licenses/Apache-2.0"}
  :dependencies [
    [clj-http "3.7.0"]
    [cljs-http "0.1.43"]
    [org.clojure/clojure "1.8.0"]
    [org.clojure/clojurescript "1.9.908"]
    [org.clojure/core.async "0.3.443"]
    [org.clojure/data.json "0.2.6"]
    [org.clojure/data.xml "0.2.0-alpha2"]]
  :profiles {
    :dev {
      :dependencies [[org.clojure/tools.namespace "0.2.11"]]
      :plugins [[lein-cljsbuild "1.1.5"]]
      :resource-paths ["dev-resources"]
      :source-paths ["dev-resources/src"]
      :repl-options {
        :init-ns cmr.client.dev}}
    :lint {
      :source-paths ^:replace ["src"]
      :test-paths ^:replace []
      :plugins [[jonase/eastwood "0.2.3"]
                [lein-ancient "0.6.10"]
                [lein-bikeshed "0.4.1"]
                [lein-kibit "0.1.2"]
                [venantius/yagni "0.1.4"]]}}
  :cljsbuild {
    :builds [{
      :id "cmr-client"
      :compiler {
        :main "cmr.client.core"
        :output-to "resources/public/js/cmr_client.js"
        :output-dir "resources/public/js"}}]}
  :aliases {
    "build-cljs"
      ^{:doc "Build just the ClojureScript code."}
      ["cljsbuild" "once" "cmr-client"]
    "rhino-repl"
      ^{:doc "Start a Rhino-based Clojurescript REPL"}
      ["trampoline" "run" "-m" "clojure.main"
       "dev-resources/src/cmr/client/dev/rhino.clj"]
    "browser-repl"
      ^{:doc "Start a browser-based Clojurescript REPL"}
      ["trampoline" "run" "-m" "clojure.main"
      "dev-resources/src/cmr/client/dev/browser.clj"]
    "check-deps"
      ^{:doc "Check to see if any dependencies are out of date"}
      ["with-profile" "lint" "ancient" "all"]})
