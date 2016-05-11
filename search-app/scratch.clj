(ns scratch
  (require [clojure.java.io :as io])
  (import [javax.script
           ScriptEngine
           ScriptEngineManager
           Invocable]
          [java.io
           StringReader
           ByteArrayInputStream]))


(def bootstrap-erb
  (io/resource "collection_preview/bootstrap.erb"))

(def test-erb
  (io/resource "collection_preview/test.erb"))

(defn initialize-jruby
  []
  (let [jruby (.. (ScriptEngineManager.)
                  (getEngineByName "jruby"))]
    (.eval jruby (io/reader bootstrap-erb))
    jruby))

(def jruby (initialize-jruby))


(defn render-erb
  [jruby erb-resource args]
  (.invokeFunction
   ^Invocable jruby
   "render"
   (to-array [(io/input-stream erb-resource) args])))

(defn render-literal-erb
  [jruby erb-str args]
  (.invokeFunction
   ^Invocable jruby
   "render"
   (to-array [(ByteArrayInputStream. (.getBytes erb-str)) args])))

(render-erb jruby test-erb {"test_value" 25.7})

(render-literal-erb
 jruby
 "<% require 'json'
  my_hash = JSON.parse('{\"hello\": \"goodbye\"}') %>
  <%= my_hash.inspect %>"
 {})
(println *1)