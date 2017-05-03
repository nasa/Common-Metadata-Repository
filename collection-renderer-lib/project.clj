(def jruby-version
  "The version of JRuby to use. This is the same as used in the echo orbits java package to prevent
   classpath issues"
  "9.1.8.0")

(def cmr-metadata-preview-repo
  "Defines the repo url of cmr_metadata_preview project"
  "https://git.earthdata.nasa.gov/scm/cmr/cmr_metadata_preview.git")

(def metadata-preview-commit
  "Defines the commit id of cmr_metadata_preview project that we should build the gem off.
   This is our contract with the MMT team in maintaining versioning of the gem without involving
   RubyGems. The environment variable CMR_METADATA_PREVIEW_COMMIT can be used to override
   the hardcoded commit id during dev integration with cmr_metadata_preview project.
   The hardcoded commit id should be updated when MMT releases a new version of the gem."
  (or (System/getenv "CMR_METADATA_PREVIEW_COMMIT")
      "6983be7"))

(def gem-install-path
  "The directory within this library where Ruby gems are installed."
  "gems")

(defproject nasa-cmr/cmr-collection-renderer-lib "0.1.0-SNAPSHOT"
  :description "Renders collections as HTML"
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/collection-renderer-lib"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [nasa-cmr/cmr-common-lib "0.1.1-SNAPSHOT"]
                 [nasa-cmr/cmr-umm-spec-lib "0.1.0-SNAPSHOT"]
                 [org.jruby/jruby-complete ~jruby-version]]
  :plugins [[lein-shell "0.4.0"]
            [test2junit "1.2.1"]]
  :resource-paths ["resources" ~gem-install-path]
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {
    :dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                         [proto-repl "0.3.1"]
                         [pjstadig/humane-test-output "0.8.1"]]
          :jvm-opts ^:replace ["-server"]
          :source-paths ["src" "dev" "test"]
          :injections [(require 'pjstadig.humane-test-output)
                       (pjstadig.humane-test-output/activate!)]}
    :docs {}
    ;; This profile is used for linting and static analysis. To run for this
    ;; project, use `lein lint` from inside the project directory. To run for
    ;; all projects at the same time, use the same command but from the top-
    ;; level directory.
    :lint {
      :source-paths ^:replace ["src"]
      :test-paths ^:replace []
      :plugins [[jonase/eastwood "0.2.3"]
                [lein-ancient "0.6.10"]
                [lein-bikeshed "0.4.1"]
                [lein-kibit "0.1.2"]
                [venantius/yagni "0.1.4"]]}}
  :aliases {"install-gems" ["shell"
                            "support/install_gems.sh"
                            ~jruby-version
                            ~cmr-metadata-preview-repo
                            ~metadata-preview-commit]
            "clean-gems" ["shell" "rm" "-rf" ~gem-install-path]
            ;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]
            ;; Linting aliases
            "kibit" ["do" ["with-profile" "lint" "shell" "echo" "== Kibit =="]
                          ["with-profile" "lint" "kibit"]]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "yagni" ["with-profile" "lint" "yagni"]
            "check-deps" ["with-profile" "lint" "ancient"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]
            ;; Placeholder for future docs and enabler of top-level alias
            "generate-docs" ["with-profile" "docs" "shell" "echo"]})
