(defn get-prompt
  [ns]
  (str "\u001B[35m[\u001B[34m"
       ns
       "\u001B[35m]\u001B[33m λ\u001B[m=> "))

(defn print-welcome
  []
  (println (slurp "dev-resources/text/banner.txt"))
  (println (slurp "dev-resources/text/loading.txt")))

(defproject gov.nasa.earthdata/cmr-process-manager "0.1.0-SNAPSHOT"
  :description "Process management functionality for CMR services"
  :url "https://github.com/cmr-exchange/dev-env-manager"
  :license {
    :name "Apache License 2.0"
    :url "https://www.apache.org/licenses/LICENSE-2.0"}
  :exclusions [org.clojure/clojure]
  :dependencies [
    [cheshire "5.8.0"]
    [clojusc/trifl "0.3.0-SNAPSHOT"]
    [clojusc/twig "0.3.2"]
    [com.stuartsierra/component "0.3.2"]
    [me.raynes/conch "0.8.0"]
    [org.clojure/clojure "1.9.0"]
    [org.clojure/core.async "0.4.474"]]
  :profiles {
    ;; Tasks
    :ubercompile {:aot :all}
    ;; Environments
    :custom-repl {
      :repl-options {
        ;:prompt ~get-prompt
        ;:welcome ~(print-welcome)
        }}
    :dev {
      :dependencies [
        [clojusc/dev-system "0.1.0"]
        [clojusc/ltest "0.3.0"]
        [org.clojure/tools.namespace "0.2.11"]]
      :source-paths [
        "dev-resources/src"]
      :repl-options {
        :init-ns cmr.process.manager.repl}}
    :test {
      :plugins [
        [jonase/eastwood "0.2.6"]
        [lein-ancient "0.6.15"]
        [lein-bikeshed "0.5.1"]
        [lein-kibit "0.1.6"]
        [venantius/yagni "0.1.4"]]}
    :lint {
      :source-paths ^:replace ["src"]}
    :docs {
      :dependencies [
        [gov.nasa.earthdata/codox-theme "1.0.0-SNAPSHOT"]]
      :plugins [
        [lein-codox "0.10.4"]
        [lein-simpleton "1.3.0"]]
      :codox {
        :project {:name "CMR Process Management"}
        :themes [:eosdis]
        :html {
          :transforms [[:head]
                       [:append
                         [:script {
                           :src "https://cdn.earthdata.nasa.gov/tophat2/tophat2.js"
                           :id "earthdata-tophat-script"
                           :data-show-fbm "true"
                           :data-show-status "true"
                           :data-status-api-url "https://status.earthdata.nasa.gov/api/v1/notifications"
                           :data-status-polling-interval "10"}]]
                       [:body]
                       [:prepend
                         [:div {:id "earthdata-tophat2"
                                :style "height: 32px;"}]]
                       [:body]
                       [:append
                         [:script {
                           :src "https://fbm.earthdata.nasa.gov/for/CMR/feedback.js"
                           :type "text/javascript"}]]]}
        :doc-paths ["resources/docs/markdown"]
        :output-path "docs/current"
        :namespaces [#"^cmr\.process\.manager\.(?!test)"]
        :metadata {:doc/format :markdown}}}}
  :aliases {
    ;; General aliases
    "repl" ["with-profile" "+custom-repl" "do"
      ["clean"]
      ["repl"]]
    "ubercompile" ["with-profile" "+ubercompile" "compile"]
    "check-deps" ["with-profile" "+test" "ancient" "check" ":all"]
    "lint" ["with-profile" "+test,+lint" "kibit"]
    "docs" ["with-profile" "+docs" "do"
      ["clean"]
      ["compile"]
      ["codox"]
      ["clean"]]
    "build" ["with-profile" "+test" "do"
      ["check-deps"]
      ["lint"]
      ["docs"]
      ["ubercompile"]
      ["clean"]
      ["uberjar"]
      ["clean"]
      ["test"]]})
