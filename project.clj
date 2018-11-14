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

(defproject gov.nasa.earthdata/cmr-metadata-proxy "0.2.0-SNAPSHOT"
  :description ~(str "A library that provides convenience functions for "
                     "accessing and locally caching CMR metadata (granules, "
                     "collections, variables, services, etc.)")
  :url "https://github.com/cmr-exchange/cmr-metadata-proxy"
  :license {
    :name "Apache License, Version 2.0"
    :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [
    [cheshire "5.8.1"]
    [clojusc/trifl "0.4.2"]
    [clojusc/twig "0.4.0"]
    [com.stuartsierra/component "0.3.2"]
    [environ "1.1.0"]
    [gov.nasa.earthdata/cmr-authz "0.1.1"]
    [gov.nasa.earthdata/cmr-exchange-common "0.2.2"]
    [gov.nasa.earthdata/cmr-exchange-query "0.2.0"]
    [gov.nasa.earthdata/cmr-http-kit "0.1.5"]
    [gov.nasa.earthdata/cmr-mission-control "0.1.0"]
    [metosin/ring-http-response "0.9.1"]
    [org.clojure/clojure "1.9.0"]
    [org.clojure/core.async "0.4.474"]
    [org.clojure/core.cache "0.7.1"]]
  :jvm-opts ["-XX:-OmitStackTraceInFastThrow"
             "-Xms2g"
             "-Xmx2g"]
  :aot [clojure.tools.logging.impl]
  :profiles {
    :ubercompile {
      :aot :all
      :source-paths ["test"]}
    :security {
      :plugins [
        [lein-nvd "0.5.6"]]
      :source-paths ^:replace ["src"]
      :nvd {
        :suppression-file "resources/security/false-positives.xml"}
      :exclusions [
        ;; The following are excluded due to their being flagged as a CVE
        [com.google.protobuf/protobuf-java]
        [com.google.javascript/closure-compiler-unshaded]
        [commons-fileupload]
        ;; The following is excluded because it stomps on twig's logger
        [org.slf4j/slf4j-simple]]
      :dependencies [
        ;; The following pull required deps that have been either been
        ;; explicitly or implicitly excluded above due to CVEs and need
        ;; declare secure versions of the libs pulled in
        [commons-fileupload "1.3.3"]
        [commons-io "2.6"]]}
    :system {
      :dependencies [
        [clojusc/system-manager "0.3.0"]]}
    :local {
      :dependencies [
        [org.clojure/tools.namespace "0.2.11"]
        [proto-repl "0.3.1"]]
      :plugins [
        [lein-project-version "0.1.0"]
        [lein-shell "0.5.0"]
        [venantius/ultra "0.5.2"]]
      :source-paths ["dev-resources/src"]
      :jvm-opts [
        "-Dlogging.color=true"]}
    :dev {
      :dependencies [
        [debugger "0.2.1"]]
      :repl-options {
        :init-ns cmr.metadata.proxy.repl
        :prompt ~get-prompt
        :init ~(println (get-banner))}}
    :lint {
      :source-paths ^:replace ["src"]
      :test-paths ^:replace []
      :plugins [
        [jonase/eastwood "0.3.3"]
        [lein-ancient "0.6.15"]
        [lein-bikeshed "0.5.1"]
        [lein-kibit "0.1.6"]
        [venantius/yagni "0.1.6"]]}
    :test {
      :dependencies [
        [clojusc/ltest "0.3.0"]]
      :plugins [
        [lein-ltest "0.3.0"]
        [test2junit "1.4.2"]]
      :jvm-opts [
        "-Dcmr.testing.config.data=testing-value"]
      :test2junit-output-dir "junit-test-results"
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
    "version" ["do"
      ["version"]
      ["shell" "echo" "-n" "CMR OUS: "]
      ["project-version"]]
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
    "junit" ["with-profile" "+test,+system" "test2junit"]
    ;; Security
    "check-sec" ["with-profile" "+system,+local,+security" "do"
      ["clean"]
      ["nvd" "check"]]
    ;; Documentation and static content
    "codox" ["with-profile" "+docs,+system" "codox"]
    "marginalia" ["with-profile" "+docs,+system"
      "marg" "--dir" "resources/public/docs/opendap/docs/current/marginalia"
             "--file" "index.html"
             "--name" "OPeNDAP/CMR Integration"]
    "docs" ["do"
      ["codox"]
      ["marginalia"]]
    ;; Build tasks
    "build-jar" ["with-profile" "+security" "jar"]
    "build-uberjar" ["with-profile" "+security" "uberjar"]
    "build-lite" ["do"
      ["ltest" ":unit"]]
    "build" ["do"
      ["clean"]
      ["check-vers"]
      ["check-sec"]
      ["ltest" ":unit"]
      ["junit" ":unit"]
      ["ubercompile"]
      ["build-uberjar"]]
    "build-full" ["do"
      ["ltest" ":unit"]
      ["generate-static"]
      ["ubercompile"]
      ["build-uberjar"]]
    ;; Installing
    "install" ["do"
      ["clean"]
      ["ubercompile"]
      ["clean"]
      ["install"]]
    ;; Publishing
    "publish" ["with-profile" "+system,+security" "do"
      ["clean"]
      ["build-jar"]
      ["deploy" "clojars"]]})
