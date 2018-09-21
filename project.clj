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

(defproject gov.nasa.earthdata/cmr-sizing-plugin "0.1.0-SNAPSHOT"
  :description "A size estimation service for subsetted GIS data"
  :url "https://github.com/cmr-exchange/cmr-sizing-plugin"
  :license {
    :name "Apache License, Version 2.0"
    :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [
    [gov.nasa.earthdata/cmr-authz "0.1.1-SNAPSHOT"]
    [gov.nasa.earthdata/cmr-exchange-common "0.2.0-SNAPSHOT"]
    [gov.nasa.earthdata/cmr-http-kit "0.1.2-SNAPSHOT"]
    [gov.nasa.earthdata/cmr-site-templates "0.1.0-SNAPSHOT"]
    [org.clojure/clojure "1.9.0"]]
  :manifest {"CMR-Plugin" "service-bridge-app"}
  :jvm-opts ["-XX:-OmitStackTraceInFastThrow"
             "-Xms2g"
             "-Xmx2g"]
  :aot [clojure.tools.logging.impl]
  :profiles {
    :ubercompile {
      :aot :all
      :source-paths ["test"]}
    :local {
      :dependencies [
        [org.clojure/tools.namespace "0.2.11"]
        [proto-repl "0.3.1"]]
      :plugins [
        [lein-shell "0.5.0"]
        [venantius/ultra "0.5.2"]]
      :source-paths ["dev-resources/src"]
      :jvm-opts [
        "-Dlogging.color=true"]}
    :dev {
      :dependencies [
        [clojusc/trifl "0.2.0"]
        [clojusc/twig "0.3.2"]
        [debugger "0.2.1"]]
      :repl-options {
        :init-ns cmr.sizing.dev
        :prompt ~get-prompt
        :init ~(println (get-banner))}}
    :lint {
      :source-paths ^:replace ["src"]
      :test-paths ^:replace []
      :plugins [
        [jonase/eastwood "0.2.8"]
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
        :unit #(not (or (:integration %) (:system %)))
        :integration :integration
        :system :system
        :default (complement :system)}}}
  :aliases {
    ;; Dev & Testing Aliases
    "repl" ["do"
      ["clean"]
      ["with-profile" "+local,+system" "repl"]]
    "ubercompile" ["with-profile" "+system,+local,+ubercompile" "compile"]
    "uberjar" ["with-profile" "+system" "uberjar"]
    "uberjar-aot" ["with-profile" "+system,+ubercompile" "uberjar"]
    "check-vers" ["with-profile" "+lint" "ancient" "check" ":all"]
    "check-jars" ["with-profile" "+lint" "do"
      ["deps" ":tree"]
      ["deps" ":plugin-tree"]]
    "check-deps" ["do"
      ["check-jars"]
      ["check-vers"]]
    "kibit" ["with-profile" "+lint" "kibit"]
    "eastwood" ["with-profile" "+lint" "eastwood" "{:namespaces [:source-paths]}"]
    "yagni" ["with-profile" "+lint" "yagni"]
    "lint" ["do"
      ["kibit"]
      ;["eastwood"]
      ]
    "ltest" ["with-profile" "+test,+system" "ltest"]
    "build" ["do"
      ["ltest" ":unit"]
      ["ubercompile"]
      ["uberjar"]]})
