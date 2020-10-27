(ns cmr.orbits.ruby-specs-test
  "Executes the Orbits RSPec tests as Clojure tests"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.orbits.orbits-runtime :as orbits-runtime])
  (:import
   (javax.script ScriptEngineManager)
   (org.jruby.embed.jsr223 JRubyEngine)))

(set! *warn-on-reflection* true)

(defn eval-jruby
  [^JRubyEngine jruby s]
  (.eval jruby (java.io.StringReader. s)))

(deftest test-ruby-specs
  ;; Find all the spec files in the test_resources/spec folder
  (let [specs (->> (io/file (io/resource "spec"))
                   (.list)
                   (filter #(str/ends-with? %"_spec.rb")))]
    (doseq [spec-name specs
            ;; Create a new instance of the JRuby runtime for each spec so that we can show separate
            ;; results for each spec.
            :let [jruby (orbits-runtime/create-jruby-runtime)]]
      (eval-jruby jruby "require 'rspec'")
      (testing spec-name
        (try
          (eval-jruby
            jruby
            (format "load 'spec/%s'" spec-name))
          (catch Exception e
            (println "The spec failed to load. You may need to install the gems in orbits lib to continue."
                     "Run lein install-gems in orbits-lib and restart your REPL.")
            (throw e)))
        ;; RSPec returns 0 when tests pass and 1 when they fail.
        (is (= 0 (eval-jruby jruby "require 'rspec/core'; RSpec::Core::Runner.run([])"))
            (str "RSPec returned failure status for " spec-name))))))
