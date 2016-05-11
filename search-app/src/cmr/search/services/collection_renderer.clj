(ns cmr.search.services.collection-renderer
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

(def bootstrap-erb
  "TODO"
  ;; TODO should this be moved or refactored to remove unused things?
  (io/resource "collection_preview/bootstrap.rb"))

(def collection-preview-erb
  "TODO"
  ;; TODO should this be moved or refactored to remove unused things?
  (io/resource "collection_preview/collection_preview.erb"))

(defn- create-jruby-runtime
  "TODO"
  []
  (let [jruby (.. (ScriptEngineManager.)
                  (getEngineByName "jruby"))]
    (.eval jruby (io/reader bootstrap-erb))
    jruby))

(comment
 (try
   (let [;context {:system (get-in user/system [:apps :search])}
         jruby (context->jruby-runtime context)]
     (.eval jruby (io/reader bootstrap-erb))
     (render-collection context collection))
   (catch Exception e
     (.printStackTrace e)
     (throw e))))

;; An wrapper component for the JRuby runtime
(defrecord ErbRenderer
  [jruby-runtime]
  l/Lifecycle

  (start
    [this _system]
    (assoc this :jruby-runtime (create-jruby-runtime)))
  (stop
    [this _system]
    (dissoc this :jruby-runtime)))

(defn create-erb-render
  "TODO"
  []
  (->ErbRenderer nil))

(defn- render-erb
  "TODO"
  [jruby-runtime erb-resource args]
  (.invokeFunction
   ^Invocable jruby-runtime
   "java_render"
   (to-array [(io/input-stream erb-resource) args])))

(defn- context->jruby-runtime
  [context]
  (get-in context [:system :erb-renderer :jruby-runtime]))

(defn render-collection
  "TODO"
  [context collection]
  (cmr.common.dev.capture-reveal/capture-all)
  (let [umm-json (umm-json/umm->json collection)]
   (render-erb (context->jruby-runtime context) collection-preview-erb {"umm_json" umm-json})))
