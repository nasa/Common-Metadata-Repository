(ns cmr.orbits.orbits-runtime
  "Defines a component which can be used to calculate Orbits"
  (require [cmr.common.lifecycle :as l]
           [clojure.java.io :as io])
  (import [javax.script
           ScriptEngine
           ScriptEngineManager
           Invocable]
          [java.io
           ByteArrayInputStream]))

(def system-key
  "The key to use when storing the orbit runtime"
  :orbits)

(def bootstrap-rb
  "A ruby script that will bootstrap the JRuby environment to contain the appropriate functions for
   generating ERB code."
  (io/resource "collection_preview/bootstrap.rb"))

(def collection-preview-erb
  "The main ERB used to generate the Collection HTML."
  (io/resource "collection_preview/collection_preview.erb"))

(defn- create-jruby-runtime
  "Creates and initializes a JRuby runtime."
  []
  (let [jruby (.. (ScriptEngineManager.)
                  (getEngineByName "jruby"))]
    ; (.eval jruby (io/reader bootstrap-rb))
    jruby))

;; Allows easily evaluating Ruby code in the Clojure REPL.
(comment
 (def jruby (create-jruby-runtime))

 (defn eval-jruby
   [s]
   (.eval jruby (java.io.StringReader. s)))

 (eval-jruby "load 'orbits/echo_orbits_impl.rb'")

 (let [lat 88.0
       lon 179.0
       ascending true
       inclination_deg 98.15
       fperiod_min 98.88
       swath_width_km 1450.0
       start_clat_deg -90.0
       count 0.5
       coords (to-array [-45, -145, -45, 145, 45, 145, 45, -145])
       coords2 (to-array [-45, 45, 45, -45])]
   (.invokeFunction
    jruby
    "areaCrossingRange"
    (to-array ["br", coords2, ascending, inclination_deg,
               fperiod_min, swath_width_km, start_clat_deg, count])))

 (eval-jruby "javascript_include_tag '/search/javascripts/application'  ")
 (eval-jruby "stylesheet_link_tag \"/search/stylesheets/application\", media: 'all' "))


; ;; An wrapper component for the JRuby runtime
; (defrecord CollectionRenderer
;   [jruby-runtime]
;   l/Lifecycle
;
;   (start
;     [this _system]
;     (assoc this :jruby-runtime (create-jruby-runtime)))
;   (stop
;     [this _system]
;     (dissoc this :jruby-runtime)))
;
; (defn create-collection-renderer
;   "Returns an instance of the collection renderer component."
;   []
;   (->CollectionRenderer nil))
;
; (defn- render-erb
;   "Renders the ERB resource with the given JRuby runtime, URL to an ERB on the classpath, and a map
;    of arguments to pass the ERB."
;   [jruby-runtime erb-resource args]
;   (.invokeFunction
;    ^Invocable jruby-runtime
;    "java_render" ;; Defined in bootstrap.rb
;    (to-array [(io/input-stream erb-resource) args])))
;
; (defn- context->jruby-runtime
;   [context]
;   (get-in context [:system system-key :jruby-runtime]))
;
; (defn- context->relative-root-url
;   [context]
;   (get-in context [:system :public-conf :relative-root-url]))
;
; (defn render-collection
;   "Renders a UMM-C collection record and returns the HTML as a string."
;   [context collection]
;   (let [umm-json (umm-json/umm->json collection)]
;    (render-erb (context->jruby-runtime context) collection-preview-erb
;                ;; Arguments for collection preview. See the ERB file for documentation.
;                {"umm_json" umm-json
;                 "relative_root_url" (context->relative-root-url context)})))
;
;
