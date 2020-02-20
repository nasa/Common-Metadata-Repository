(def jruby-version
  "The version of JRuby to use. This is the same as used in the echo orbits java package to prevent
   classpath issues"
  "9.2.6.0")

(def metadata-preview-info
  "Defines the commit id, version, and repo of cmr_metadata_preview project that we should build the gem off.
   This is our contract with the MMT team in maintaining versioning of the gem without involving
   RubyGems. The environment variable CMR_METADATA_PREVIEW_COMMIT can be used to override
   the hardcoded commit id during dev integration with cmr_metadata_preview project.
   The hardcoded commit id should be updated when MMT releases a new version of the gem."
  {:repo "https://git.earthdata.nasa.gov/scm/cmr/cmr_metadata_preview.git"
   :version "cmr_metadata_preview-0.2.0"
   :commit-id (or (System/getenv "CMR_METADATA_PREVIEW_COMMIT")
                  "91cd5b93d51")})

(def gem-install-path
  "The directory within this library where Ruby gems are installed."
  "gems")

(defproject nasa-cmr/cmr-collection-renderer-lib "0.1.0-SNAPSHOT"
  :description "Renders collections as HTML"
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/collection-renderer-lib"
  :exclusions [[commons-io]]
  :dependencies [[commons-io "2.6"]
                 [nasa-cmr/cmr-common-app-lib "0.1.0-SNAPSHOT"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-umm-spec-lib "0.1.0-SNAPSHOT"]
                 [org.clojure/clojure "1.10.0"]
                 [org.jruby/jruby-complete ~jruby-version]]
  :plugins [[lein-shell "0.5.0"]
            [test2junit "1.3.3"]]
  :resource-paths ["resources"
                   ~gem-install-path
                   ~(str gem-install-path "/gems/" (:version metadata-preview-info))]
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :jar-inclusions [#"\.umm-version"]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.1.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"
                                           :properties-file "resources/security/dependencycheck.properties"}}
             :dev {:exclusions [[org.clojure/tools.nrepl]]
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/tools.nrepl "0.2.13"]
                                  [pjstadig/humane-test-output "0.9.0"]
                                  [proto-repl "0.3.1"]]
                   :jvm-opts ^:replace ["-server"]
                   :source-paths ["src" "dev" "test"]
                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]}
             :static {}
             ;; This profile is used for linting and static analysis. To run for this
             ;; project, use `lein lint` from inside the project directory. To run for
             ;; all projects at the same time, use the same command but from the top-
             ;; level directory.
             :lint {:source-paths ^:replace ["src"]
                    :test-paths ^:replace []
                    :plugins [[jonase/eastwood "0.2.5"]
                              [lein-ancient "0.6.15"]
                              [lein-bikeshed "0.5.0"]
                              [lein-kibit "0.1.6"]
                              [venantius/yagni "0.1.4"]]}
             ;; The following profile is overriden on the build server or in the user's
             ;; ~/.lein/profiles.clj file.
             :internal-repos {}}
  :aliases {"install-gems" ["shell"
                            "support/install_gems.sh"
                            ~jruby-version
                            ~(:repo metadata-preview-info)
                            ~(:commit-id metadata-preview-info)
                            ~(:version metadata-preview-info)]
            "clean-gems" ["shell" "rm" "-rf" ~gem-install-path]
            "install" ["do" "clean-gems," "install-gems," "install," "clean"]
            "install!" "install"
            "internal-install!" ["with-profile" "+internal-repos" "do"
                                  ["clean-gems" "install-gems" "install" "clean"]]
            ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]
            ;; Linting aliases
            "kibit" ["do" ["with-profile" "lint" "shell" "echo" "== Kibit =="]
                          ["with-profile" "lint" "kibit"]]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "yagni" ["with-profile" "lint" "yagni"]
            "check-deps" ["with-profile" "lint" "ancient" ":all"]
            "check-sec" ["with-profile" "security" "dependency-check"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]
            ;; Placeholder for future docs and enabler of top-level alias
            "generate-static" ["with-profile" "static" "shell" "echo"]})
