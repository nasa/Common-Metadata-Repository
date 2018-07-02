(defproject gov.nasa.earthdata/cmr-authz "0.1.1-SNAPSHOT"
  :description "An authorization utility library for CMR services"
  :url "https://github.com/cmr-exchange/authz"
  :license {
    :name "Apache License, Version 2.0"
    :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [
    [cheshire "5.8.0"]
    [clojusc/trifl "0.2.0"]
    [clojusc/twig "0.3.2"]
    [com.stuartsierra/component "0.3.2"]
    [gov.nasa.earthdata/cmr-http-kit "0.1.1-SNAPSHOT"]
    [http-kit "2.3.0"]
    [metosin/reitit-ring "0.1.1-SNAPSHOT"]
    [org.clojure/clojure "1.9.0"]
    [org.clojure/core.cache "0.7.1"]
    [org.clojure/data.xml "0.2.0-alpha5"]
    [tolitius/xml-in "0.1.0"]]
  :profiles {
    :ubercompile {
      :aot :all
      :source-paths ["test"]}
    :lint {
      :source-paths ^:replace ["src"]
      :test-paths ^:replace []
      :plugins [
        [jonase/eastwood "0.2.5"]
        [lein-ancient "0.6.15"]
        [lein-kibit "0.1.6"]]}
    :test {
      :dependencies [
        [clojusc/ltest "0.3.0"]]
      :plugins [
        [lein-ltest "0.3.0"]]
      :test-selectors {
        :unit #(not (or (:integration %) (:system %)))
        :integration :integration
        :system :system
        :default (complement :system)}}}
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
      ["kibit"]
      ;["eastwood"]
      ]
    "ltest" ["with-profile" "+test,+system" "ltest"]
    ;; Documentation and static content
    ;; Build tasks
    "build-lite" ["do"
      ["ltest" ":unit"]]
    "build" ["do"
      ["clean"]
      ["ltest" ":unit"]
      ["ubercompile"]
      ["uberjar"]]
    "build-full" ["do"
      ["ltest" ":unit"]
      ["ubercompile"]
      ["uberjar"]]})
