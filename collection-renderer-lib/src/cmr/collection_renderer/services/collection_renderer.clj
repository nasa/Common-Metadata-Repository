(ns cmr.collection-renderer.services.collection-renderer
  "Defines a component which can be used to generate an HTML response of a UMM-C collection. Uses the
   MMT ERB code along with JRuby to generate it."
  (:require
   [clojure.java.io :as io]
   [cmr.common.lifecycle :as l]
   [cmr.umm-spec.migration.version-migration :as vm]
   [cmr.umm-spec.umm-json :as umm-json]
   [cmr.umm-spec.versioning :as umm-version])
  (:import
   (java.io ByteArrayInputStream)
   (javax.script ScriptEngine ScriptEngineManager Invocable)))

(def system-key
  "The key to use when storing the collection renderer"
  :collection-renderer)

(def bootstrap-rb
  "A ruby script that will bootstrap the JRuby environment to contain the appropriate functions for
   generating ERB code."
  (io/resource "collection_preview/bootstrap.rb"))

(def collection-preview-erb
  "The main ERB used to generate the Collection HTML."
  (io/resource "collection_preview/collection_preview.erb"))

(def preview-gem-umm-schema-version
  "Temporay def for the UMM schema version that is supported by the CMR preview gem.
   This will be removed once CMR preview gem is fixed to include the UMM schema version
   it supports in the gem. (MMT-921)"
  "1.6")

(defn- create-jruby-runtime
  "Creates and initializes a JRuby runtime."
  []
  (let [jruby (.. (ScriptEngineManager.)
                  (getEngineByName "jruby"))]
    (.eval jruby (io/reader bootstrap-rb))
    jruby))

;; Allows easily evaluating Ruby code in the Clojure REPL.
(comment
 (def jruby (create-jruby-runtime))

 (defn eval-jruby
   [s]
   (.eval jruby (java.io.StringReader. s)))

 (eval-jruby "javascript_include_tag '/search/javascripts/application'  ")
 (eval-jruby "stylesheet_link_tag \"/search/stylesheets/application\", media: 'all' "))


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
  "Returns an instance of the collection renderer component."
  []
  (->CollectionRenderer nil))

(defn- render-erb
  "Renders the ERB resource with the given JRuby runtime, URL to an ERB on the classpath, and a map
   of arguments to pass the ERB."
  [jruby-runtime erb-resource args]
  (.invokeFunction
   ^Invocable jruby-runtime
   "java_render" ;; Defined in bootstrap.rb
   (to-array [(io/input-stream erb-resource) args])))

(defn- context->jruby-runtime
  [context]
  (get-in context [:system system-key :jruby-runtime]))

(defn- context->relative-root-url
  [context]
  (get-in context [:system :public-conf :relative-root-url]))

(defn render-collection
  "Renders a UMM-C collection record and returns the HTML as a string."
  [context collection]
  (let [umm-json (umm-json/umm->json
                  (vm/migrate-umm context
                                  :collection
                                  umm-version/current-version
                                  preview-gem-umm-schema-version
                                  collection))]
    (render-erb (context->jruby-runtime context)
                collection-preview-erb
                ;; Arguments for collection preview. See the ERB file for documentation.
                {"umm_json" umm-json
                 "relative_root_url" (context->relative-root-url context)})))
