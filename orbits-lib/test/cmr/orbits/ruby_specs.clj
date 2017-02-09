(ns cmr.orbits.ruby-specs
  "Executes the Orbits RSPec tests as Clojure tests"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer :all])
  (:import
   (javax.script
    ScriptEngineManager)))


(defn- create-jruby-runtime
  "Creates and initializes a JRuby runtime."
  []
  (.getEngineByName (ScriptEngineManager.) "jruby"))

(defn eval-jruby
  [jruby s]
  (.eval jruby (java.io.StringReader. s)))

(deftest test-ruby-specs
  ;; Find all the spec files in the test_resources/spec folder
  (let [specs (->> (io/file (io/resource "spec"))
                   (.list)
                   (filter #(str/ends-with? %"_spec.rb")))]
    (doseq [spec-name specs
            ;; Create a new instance of the JRuby runtime for each spec so that we can show separate
            ;; results for each spec.
            :let [jruby (create-jruby-runtime)]]
      (testing spec-name
       (eval-jruby
        jruby
        (format "load 'spec/%s'" spec-name))
       ;; RSPec returns 0 when tests pass and 1 when they fail.
       (is (= 0 (eval-jruby jruby "require 'rspec/core'; RSpec::Core::Runner.run([])"))
           (str "RSPec returned failure status for " spec-name))))))





