(defn get-banner
  []
  (try
    (str
      (slurp "resources/text/banner.txt"))
      ;(slurp "resources/text/loading.txt")

    ;; If another project can't find the banner, just skip it.
    (catch Exception _ "")))

(defn get-prompt
  [ns]
  (str "\u001B[35m[\u001B[34m"
       ns
       "\u001B[35m]\u001B[33m λ\u001B[m=> "))

(defproject gov.nasa.earthdata/cmr-process-manager "0.1.1-SNAPSHOT"
  :description "Process management functionality for CMR services"
  :url "https://github.com/cmr-exchange/dev-env-manager"
  :license {:name "Apache License 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"}
  :exclusions [org.clojure/clojure]
  :dependencies [[cheshire "5.8.1"]
                 [clojusc/trifl "0.4.0"]
                 [clojusc/twig "0.4.0"]
                 [com.stuartsierra/component "0.3.2"]
                 [gov.nasa.earthdata/cmr-exchange-common "0.3.3"]
                 [me.raynes/conch "0.8.0"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.474"]]
  :profiles {
             ;; Tasks
             :ubercompile {:aot :all}
             :security {:plugins [[com.livingsocial/lein-dependency-check "1.1.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"}
                        :source-paths ^:replace ["src"]
                        :exclusions [
                                     ;; The following are excluded due to their being flagged as a CVE
                                     [com.google.protobuf/protobuf-java]
                                     [com.google.javascript/closure-compiler-unshaded]]}
             ;; Environments
             :custom-repl {:repl-options {:prompt ~get-prompt}}
             ;:welcome ~(print-welcome)
             :dev {:dependencies [[clojusc/ltest "0.3.0"]
                                  [clojusc/system-manager "0.3.0-SNAPSHOT"]
                                  [org.clojure/tools.namespace "0.2.11"]]
                   :source-paths ["dev-resources/src"]
                   :repl-options {:init-ns cmr.process.manager.repl
                                  :prompt ~get-prompt
                                  :init ~(println (get-banner))}}
             :lint {:source-paths ^:replace ["src"]
                    :test-paths ^:replace []
                    :plugins [[jonase/eastwood "0.3.3"]
                              [lein-ancient "0.6.15"]
                              [lein-kibit "0.1.8"]]}
             :test {:dependencies [[clojusc/ltest "0.3.0"]]
                    :plugins [[lein-ltest "0.3.0"]]
                    :test-selectors {:unit #(not (or (:integration %) (:system %)))
                                     :integration :integration
                                     :system :system
                                     :default (complement :system)}}
             :docs {:dependencies [[gov.nasa.earthdata/codox-theme "1.0.0-SNAPSHOT"]]
                    :plugins [[lein-codox "0.10.5"]
                              [lein-simpleton "1.3.0"]]
                    :codox {:project {:name "CMR Process Management"}
                            :themes [:eosdis]
                            :html {:transforms [[:head]
                                                [:append
                                                  [:script {:src "https://cdn.earthdata.nasa.gov/tophat2/tophat2.js"
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
                                                  [:script {:src "https://fbm.earthdata.nasa.gov/for/CMR/feedback.js"
                                                            :type "text/javascript"}]]]}
                            :doc-paths ["resources/docs/markdown"]
                            :output-path "docs/current"
                            :namespaces [#"^cmr\.process\.manager\.(?!test)"]
                            :metadata {:doc/format :markdown}}}}
  :aliases {
            ;; Dev & Testing Aliases
            "repl" ["with-profile" "+custom-repl" "do"
                    ["clean"]
                    ["repl"]]
            "ubercompile" ["with-profile" "+ubercompile,+security" "compile"]
            "check-vers" ["with-profile" "+lint" "ancient" "check" ":all"]
            "check-jars" ["with-profile" "+lint" "do"
                          ["deps" ":tree"]
                          ["deps" ":plugin-tree"]]
            "check-deps" ["do"
                          ["check-jars"]
                          ["check-vers"]]
            "ltest" ["with-profile" "+test,+system,+security" "ltest"]
            ;; Linting
            "kibit" ["with-profile" "+lint" "kibit"]
            "eastwood" ["with-profile" "+lint" "eastwood" "{:namespaces [:source-paths]}"]
            "lint" ["do"
                    ["kibit"]]
                    ;["eastwood"]
            ;; Security
            "check-sec" ["with-profile" "+security" "do"
                         ["clean"]
                         ["dependency-check"]]
            ;; Build tasks
            "build-jar" ["with-profile" "+security" "jar"]
            "build-uberjar" ["with-profile" "+security" "uberjar"]
            "docs" ["with-profile" "+docs" "do"
                    ["clean"]
                    ["compile"]
                    ["codox"]
                    ["clean"]]
            "build-lite" ["do"
                          ["ltest" ":unit"]]
            "build" ["do"
                     ["clean"]
                     ["check-vers"]
                     ["check-sec"]
                     ["ltest" ":unit"]
                     ["ubercompile"]
                     ["build-uberjar"]]
            "build-full" ["do"
                          ["docs"]
                          ["build"]]
            ;; Publishing
            "publish" ["with-profile" "+security" "do"
                       ["clean"]
                       ["build-jar"]
                       ["deploy" "clojars"]]})
