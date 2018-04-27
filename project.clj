(defn get-banner
  []
  (try
    (str
      (slurp "resources/text/banner.txt")
      ;(slurp "resources/text/loading.txt")
      )
    ;; If another project can't find the banner, just skip it;
    ;; this function is really only meant to be used by Dragon itself.
    (catch Exception _ "")))

(defn get-prompt
  [ns]
  (str "\u001B[35m[\u001B[34m"
       ns
       "\u001B[35m]\u001B[33m λ\u001B[m=> "))

(defproject gov.nasa.earthdata/cmr-opendap "0.1.0-SNAPSHOT"
  :description "OPeNDAP Integration in the CMR"
  :url "https://github.com/cmr-exchange/cmr-opendap"
  :license {
    :name "Apache License, Version 2.0"
    :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [
    [cheshire "5.8.0"]
    [clojusc/trifl "0.2.0"]
    [clojusc/twig "0.3.2"]
    [com.stuartsierra/component "0.3.2"]
    [environ "1.1.0"]
    [gov.nasa.earthdata/cmr-site-templates "0.1.0-SNAPSHOT"]
    [http-kit "2.3.0"]
    [markdown-clj "1.0.2"]
    [metosin/reitit-core "0.1.1-SNAPSHOT"]
    [metosin/reitit-ring "0.1.1-SNAPSHOT"]
    [metosin/ring-http-response "0.9.0"]
    [org.clojure/clojure "1.9.0"]
    [org.clojure/core.cache "0.7.1"]
    [org.clojure/data.xml "0.2.0-alpha5"]
    [ring/ring-core "1.6.3"]
    [ring/ring-codec "1.1.0"]
    [ring/ring-defaults "0.3.1"]
    [selmer "1.11.7"]]
  :jvm-opts ["-XX:-OmitStackTraceInFastThrow"
             "-Xms2g"
             "-Xmx2g"]
  :main cmr.opendap.core
  :aot [cmr.opendap.core]
  :profiles {
    :ubercompile {
      :aot :all
      :source-paths ["test"]}
    :system {
      :dependencies [
        [clojusc/dev-system "0.1.0"]]}
    :local {
      :dependencies [
        [org.clojure/tools.namespace "0.2.11"]
        [proto-repl "0.3.1"]]
      :plugins [
        [lein-project-version "0.1.0"]
        [lein-shell "0.5.0"]
        [venantius/ultra "0.5.2"]]
      :source-paths ["dev-resources/src"]}
    :dev {
      :jvm-opts ["-Dcmr.testing.config.data=testing-value"]
      :repl-options {
        :init-ns cmr.opendap.dev
        :prompt ~get-prompt
        :init ~(println (get-banner))}}
    :lint {
      :source-paths ^:replace ["src"]
      :test-paths ^:replace []
      :plugins [
        [jonase/eastwood "0.2.5"]
        [lein-ancient "0.6.15"]
        [lein-bikeshed "0.5.1"]
        [lein-kibit "0.1.6"]
        [venantius/yagni "0.1.4"]]}
    :test {
      :dependencies [
        [clojusc/ltest "0.3.0"]]
      :plugins [
        [lein-ltest "0.3.0"]
        [test2junit "1.4.0"]]
      :test2junit-output-dir "junit-test-results"
      :test-selectors {
        :unit #(not (or (:integration %) (:system %)))
        :integration :integration
        :system :system
        :default (complement :system)}}
    :docs {
      :dependencies [
        [clojang/codox-theme "0.2.0-SNAPSHOT"]]
      :plugins [
        [lein-codox "0.10.3"]
        [lein-marginalia "0.9.1"]]
      :source-paths ["resources/docs/src"]
      :codox {
        :project {
          :name "CMR OPeNDAP"
          :description "OPeNDAP/CMR Integration"}
        :namespaces [#"^cmr\.opendap\.(?!dev)"]
        :metadata {
          :doc/format :markdown
          :doc "Documentation forthcoming"}
        :themes [:clojang]
        :doc-paths ["resources/docs/markdown"]
        :output-path "resources/public/docs/opendap/docs/reference"}}}
  :aliases {
    ;; Dev & Testing Aliases
    "repl" ["do"
      ["clean"]
      ["with-profile" "+local,+system" "repl"]]
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
      ["kibit"]
      ;["eastwood"]
      ]
    "ltest" ["with-profile" "+test,+system" "ltest"]
    "junit" ["with-profile" "+test,+system" "test2junit"]
    ;; Documentation
    "codox" ["with-profile" "+docs" "codox"]
    "marginalia" ["with-profile" "+docs"
      "marg" "--dir" "resources/public/docs/opendap/docs/marginalia"
             "--file" "index.html"
             "--name" "OPeNDAP/CMR Integration"]
    "generate-html" ["with-profile" "+docs"
      "run" "-m" "cmr.opendap.site.static"]
    "generate-static" ["do"
      ["codox"]
      ["marginalia"]
      ["generate-html"]]
    "version" ["do"
      ["version"]
      ["shell" "echo" "-n" "CMR-OPeNDAP: "]
      ["project-version"]]
    "build-lite" ["do"
      ["ltest" ":unit"]
      ["junit" ":unit"]]
    "build" ["do"
      ["ltest" ":unit"]
      ["junit" ":unit"]
      ["generate-static"]
      ["ubercompile"]
      ["uberjar"]]

    ;; Application
    "start-cmr-opendap"
      ["trampoline" "run"]})
