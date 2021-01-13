(defproject gov.nasa.earthdata/cmr-site-templates "0.1.1-SNAPSHOT"
  :description "Selmer templates for CMR documentation, directory pages, and various static web content"
  :url "https://github.com/nasa/Common-Metadata-Repository/site-templates"
  :license {
            :name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.10.0"]]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.1.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"}}
             :test {:plugins [[lein-shell "0.5.0"]
                              [test2junit "1.4.0"]]}}

  :aliases {;; Kaocha test aliases
            ;; refer to tests.edn for test configuration
            "kaocha" ["check"]
            "itest" ["kaocha"]
            "utest" ["kaocha"]
            "ci-test" ["kaocha"]
            "ci-itest" ["itest"]
            "ci-utest" ["utest"]

            ;; The following aliases are needed for the CMR build process.
            "generate-static" ["with-profile" "+test" "shell" "echo" "NO OP"]
            "check-sec" ["with-profile" "security" "dependency-check"]
            "test-out" ["with-profile" "+test" "test2junit"]})
