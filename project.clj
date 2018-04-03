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
    [clj-http "3.8.0"]
    [clojusc/trifl "0.2.0"]
    [clojusc/twig "0.3.2"]
    [com.stuartsierra/component "0.3.2"]
    [http-kit "2.2.0"]
    [metosin/reitit-core "0.1.1-SNAPSHOT"]
    [metosin/reitit-ring "0.1.1-SNAPSHOT"]
    [metosin/ring-http-response "0.9.0"]
    [org.clojure/clojure "1.9.0"]
    [ring/ring-core "1.6.3"]
    [ring/ring-codec "1.1.0"]
    [ring/ring-defaults "0.3.1"]]
  :main cmr.opendap.core
  :profiles {
    :ubercompile {
      :aot :all}
    :dev {
      :dependencies [
        [clojusc/dev-system "0.1.0"]
        [org.clojure/tools.namespace "0.2.11"]
        [proto-repl "0.3.1"]]
      :plugins [
        [lein-shell "0.5.0"]
        [venantius/ultra "0.5.2"]]
      :source-paths ["dev-resources/src"]
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
        [lein-ltest "0.3.0"]]
      :test-selectors {
        :select :select}}
    :docs {
      :dependencies [
        [clojang/codox-theme "0.2.0-SNAPSHOT"]]
      :plugins [
        [lein-codox "0.10.3"]
        [lein-marginalia "0.9.1"]]
      :codox {
        :project {
          :name "CMR OPeNDAP"
          :description "OPeNDAP Integration in the CMR"}
        :namespaces [#"^cmr\.opendap\.(?!dev)"]
        :metadata {
          :doc/format :markdown
          :doc "Documentation forthcoming"}
        :themes [:clojang]
        :doc-paths ["resources/docs"]
        :output-path "docs/current"}}}
  :aliases {
    ;; Dev & Testing Aliases
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
    "ltest" ["with-profile" "+test" "ltest"]
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
    ;; Application
    "start-cmr-opendap"
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
