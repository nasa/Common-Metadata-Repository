(ns cmr.collection-renderer.services.collection-renderer
  "TODO"
  (require [cmr.common.lifecycle :as l]
           [clojure.java.io :as io]
           [cmr.umm-spec.umm-json :as umm-json])
  (import [javax.script
           ScriptEngine
           ScriptEngineManager
           Invocable]
          [java.io
           ByteArrayInputStream]))

(def system-key
  "The key to use when storing the collection renderer"
  :collection-renderer)

(def bootstrap-erb
  "TODO"
  (io/resource "collection_preview/bootstrap.rb"))

(def collection-preview-erb
  "TODO"
  (io/resource "collection_preview/collection_preview.erb"))

(defn- create-jruby-runtime
  "TODO"
  []
  (let [jruby (.. (ScriptEngineManager.)
                  (getEngineByName "jruby"))]
    (.eval jruby (io/reader bootstrap-erb))
    jruby))

;; An wrapper component for the JRuby runtime
(defrecord CollectionRenderer
  [jruby-runtime]
  l/Lifecycle

  (start
    [this _system]
    (assoc this :jruby-runtime (create-jruby-runtime)))
  (stop
    [this _system]
    (dissoc this :jruby-runtime)))

(defn create-collection-renderer
  "TODO"
  []
  (->CollectionRenderer nil))

(defn- render-erb
  "TODO"
  [jruby-runtime erb-resource args]
  (.invokeFunction
   ^Invocable jruby-runtime
   "java_render"
   (to-array [(io/input-stream erb-resource) args])))

(defn- context->jruby-runtime
  [context]
  (get-in context [:system system-key :jruby-runtime]))

(defn render-collection
  "TODO"
  [context collection]
  (let [umm-json (umm-json/umm->json collection)]
   (render-erb (context->jruby-runtime context) collection-preview-erb {"umm_json" umm-json})))
