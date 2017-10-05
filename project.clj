(defn get-prompt
  [ns]
  (str "\u001B[35m[\u001B[34m"
       ns
       "\u001B[35m]\u001B[33m λ\u001B[m=> "))

(defproject gov.nasa.earthdata/cmr-sample-data "0.1.0-SNAPSHOT"
  :description "Sample Data for the open source NASA Common Metadata Repository (CMR)"
  :url "https://github.com/oubiwann/cmr-sample-data"
  :license {:name "Apache License 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [
    [cheshire "5.8.0"]
    [clojusc/trifl "0.1.0"]
    [org.clojure/clojure "1.8.0"]]
  :profiles {
    :uberjar {:aot :all}
    :dev {
      :dependencies [
        [org.clojure/tools.namespace "0.2.11"]]
      :source-paths ["dev-resources/src"]
      :repl-options {
        :init-ns cmr.sample-data.dev
        :prompt ~get-prompt}}})
