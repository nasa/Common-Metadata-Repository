(ns cmr.orbits.ruby-specs
  "Executes the Orbits RSPec tests as Clojure tests"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer :all])
  (:import
   (javax.script
    Invocable
    ScriptEngine
    ScriptEngineManager)
   (java.io
    ByteArrayInputStream)))

(defn- create-jruby-runtime
  "Creates and initializes a JRuby runtime."
  []
  (.getEngineByName (ScriptEngineManager.) "jruby"))

(defn eval-jruby
  [jruby s]
  (.eval jruby (java.io.StringReader. s)))


(deftest test-ruby-specs
  (let [specs (->> (io/file (io/resource "spec"))
                   (.list)
                   (filter #(str/ends-with? %"_spec.rb")))]
    (doseq [spec-name specs
            :let [jruby (create-jruby-runtime)]]
      (eval-jruby
       jruby
       (format "load 'spec/%s'" spec-name))
      (is (= 0 (eval-jruby jruby "require 'rspec/core'; RSpec::Core::Runner.run([])"))))))





