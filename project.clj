(defn get-prompt
  [ns]
  (str "\u001B[35m[\u001B[34m"
       ns
       "\u001B[35m]\u001B[33m λ\u001B[m=> "))

(defproject gov.nasa.earthdata/cmr-dev-env-manager "0.1.0-SNAPSHOT"
  :description "An Alternate Development Environment Manager for the CMR"
  :url "https://github.com/cmr-exchange/dev-env-manager"
  :license {
    :name "Apache License 2.0"
    :url "https://www.apache.org/licenses/LICENSE-2.0"}
  :exclusions [org.clojure/clojure]
  :dependencies [
    [com.stuartsierra/component "0.3.2"]
    [leiningen-core "2.7.1"]
    [org.clojure/clojure "1.8.0"]]
  :dem {
    :logging {
      :nss [cmr]
      :level :info}}
  :profiles {
    :ubercompile {:aot :all}
    :dev {
      :dependencies [
        [clojusc/ltest "0.3.0-SNAPSHOT"]
        [clojusc/trifl "0.2.0"]
        [clojusc/twig "0.3.2"]
        [org.clojure/tools.namespace "0.2.11"]]
      :source-paths ["dev-resources/src"]
      :repl-options {
        :init-ns cmr.dev.env.manager.repl
        :prompt ~get-prompt}}
    :test {
      :plugins [
        [lein-ancient "0.6.14"]
        [jonase/eastwood "0.2.5"]
        [lein-bikeshed "0.5.0"]
        [lein-kibit "0.1.6"]
        [venantius/yagni "0.1.4"]]}}
  :aliases {
    "ubercompile" ["with-profile" "+ubercompile" "compile"]
    "check-deps" ["with-profile" "+test" "ancient" "check" ":all"]
    "lint" ["with-profile" "+test" "kibit"]
    "build" ["with-profile" "+test" "do"
      ["check-deps"]
      ["lint"]
      ["ubercompile"]
      ["clean"]
      ["uberjar"]
      ["clean"]
      ["test"]]})
