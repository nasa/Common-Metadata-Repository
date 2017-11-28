(defn get-prompt
  [ns]
  (str "\u001B[35m[\u001B[34m"
       ns
       "\u001B[35m]\u001B[33m λ\u001B[m=> "))

(defn print-welcome
  []
  (println (slurp "dev-resources/text/banner.txt"))
  (println (slurp "dev-resources/text/loading.txt")))

(defproject gov.nasa.earthdata/cmr-dev-env-manager "0.1.0-SNAPSHOT"
  :description "An Alternate Development Environment Manager for the CMR"
  :url "https://github.com/cmr-exchange/dev-env-manager"
  :license {
    :name "Apache License 2.0"
    :url "https://www.apache.org/licenses/LICENSE-2.0"}
  :exclusions [org.clojure/clojure]
  :dependencies [
    [com.stuartsierra/component "0.3.2"]
    [leiningen-core "2.7.1" :exclusions [
      commons-io
      org.apache.httpcomponents/httpcore
      org.slf4j/slf4j-nop]]
    [org.clojure/clojure "1.8.0"]
    [org.clojure/core.async "0.3.443" :exclusions [
      org.clojure/tools.reader]]]
  :dem {
    :logging {
      :level :debug}}
  :profiles {
    ;; Tasks
    :ubercompile {:aot :all}
    ;; Environments
    :dev {
      :dependencies [
        [clojusc/ltest "0.3.0-SNAPSHOT"]
        [clojusc/trifl "0.2.0"]
        [clojusc/twig "0.3.2"]
        [me.raynes/conch "0.8.0"]
        [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT" :exclusions [
          com.dadrox/quiet-slf4j
          com.google.code.findbugs/jsr305
          gorilla-repl
          org.slf4j/slf4j-nop]]
        [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT" :exclusions [
          commons-io]]
        [org.clojure/tools.namespace "0.2.11"]]
      :source-paths [
        "dev-resources/src"
        "libs/common-lib/src"
        "libs/transmit-lib/src"]
      :repl-options {
        :init-ns cmr.dev.env.manager.repl
        :prompt ~get-prompt}
        :welcome ~(print-welcome)}
    :test {
      :plugins [
        [lein-ancient "0.6.14"]
        [jonase/eastwood "0.2.5"]
        [lein-bikeshed "0.5.0"]
        [lein-kibit "0.1.6"]
        [venantius/yagni "0.1.4"]]}
    :lint {
      :source-paths ^:replace ["src"]}
    ;; Applications
    :mock-echo {
      :main cmr.mock-echo.runner
      :source-paths [
        "apps/mock-echo-app/src"
        "libs/common-app-lib/src"
        "libs/common-lib/src"
        "libs/transmit-lib"]}}
  :aliases {
    ;; Applications
    "mock-echo" ["with-profile" "+dev,+mock-echo" "run"]
    ;; General
    "ubercompile" ["with-profile" "+ubercompile" "compile"]
    "check-deps" ["with-profile" "+test" "ancient" "check" ":all"]
    "lint" ["with-profile" "+test,+lint" "kibit"]
    "build" ["with-profile" "+test" "do"
      ["check-deps"]
      ["lint"]
      ["ubercompile"]
      ["clean"]
      ["uberjar"]
      ["clean"]
      ["test"]]})
