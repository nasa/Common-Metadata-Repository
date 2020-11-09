(ns cmr.collection-renderer.services.collection-renderer
  "Defines a component which can be used to generate an HTML response of a UMM-C collection. Uses the
   MMT ERB code along with JRuby to generate it."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.lifecycle :as l]
   [cmr.common.log :refer [info]]
   [cmr.common-app.services.search :as search]
   [cmr.common-app.services.search.params :as params]
   [cmr.umm-spec.migration.version.core :as vm]
   [cmr.umm-spec.umm-json :as umm-json]
   [cmr.umm-spec.versioning :as umm-version]
   [cmr.transmit.config :as config])
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

(def preview-gem-umm-version-config-file
  "Defines the path to the UMM schema version config file within the preview gem."
  ".umm-version")

(def ^:private create-jruby-runtime
  "Creates and initializes a JRuby runtime. We do this as a future to speed up REPL start time
  by more than one minute. Worst case is the first request to retrieve HTML after the search app
  starts takes one minute, but in practice NGAP usually takes around one minute from the time an app
  is started to put it in the load balancer, so this is not an operational concern."
  (future
    (let [jruby (.. (ScriptEngineManager.)
                    (getEngineByName "jruby"))
          cmd (format "load '%s'" (.getFile bootstrap-rb))]
      (.eval jruby cmd)
      (info "JRuby runtime ready for collection renderer.")
      jruby)))

(defn- get-preview-gem-umm-version
  "Get the UMM schema version that is defined in preview gem."
  []
  (-> preview-gem-umm-version-config-file
      io/resource
      slurp
      str/trim))

;; Allows easily evaluating Ruby code in the Clojure REPL.
(comment
 (def jruby create-jruby-runtime)

 (defn eval-jruby
   [s]
   (.eval (deref jruby) (java.io.StringReader. s)))

 (eval-jruby "javascript_include_tag '/search/javascripts/application'  ")
 (eval-jruby "stylesheet_link_tag \"/search/stylesheets/application\", media: 'all' "))


;; An wrapper component for the JRuby runtime
(defrecord CollectionRenderer
  [jruby-runtime]
  l/Lifecycle

  (start
   [this _system]
   (-> this
       (assoc :jruby-runtime create-jruby-runtime)
       (assoc :preview-gem-umm-version (get-preview-gem-umm-version))))


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
  (deref (get-in context [:system system-key :jruby-runtime])))

(defn- context->relative-root-url
  [context]
  (get-in context [:system :public-conf :relative-root-url]))

(defn- context->preview-gem-umm-version
  [context]
  (get-in context [:system system-key :preview-gem-umm-version]))

(defconfig search-edsc-url
  "URL of the Earthdata Search application"
  {:default "https://search.earthdata.nasa.gov/search"})

(defn- get-granule-count-for-collection
  "Retrieve granule count for the collection corresponding to concept-id."
  [context concept-id]
  (->> {:page-size 0
        :collection-concept-id concept-id}
       (params/parse-parameter-query context :granule)
       (search/find-concepts context :granule)
       :hits))

(defn- get-associations-for-collection
  "Retrieve association for the collection corresponding to concept-id and assoc-type."
  [context concept-id assoc-type]
  (let [collection-results (->> {:concept-id concept-id
                                 :result-format {:format :umm-json-results}}
                                (params/parse-parameter-query context :collection)
                                (search/find-concepts context :collection)
                                :results)
        items (:items (json/parse-string collection-results true))
        assoc-types (keyword (str (name assoc-type) "s"))
        ;; service or tool concept-ids associated to the collection.
        assoc-ids (get-in (first items) [:meta :associations assoc-types])]
    (for [assoc-id assoc-ids
          :let [assoc-results (->> {:concept-id assoc-id
                                    :result-format {:format :umm-json-results}}
                                   (params/parse-parameter-query context assoc-type)
                                   (search/find-concepts context assoc-type)
                                   :results)
                items (:items (json/parse-string assoc-results true))]]
      (first items))))

(defn- get-additional-information
  "Retrieve additional information for collection rendering."
  [context concept-id]
  {"granule_count" (get-granule-count-for-collection context concept-id)
   "root_url" (config/application-public-root-url context)
   "services" (map #(umm-json/umm->json %)
                   (get-associations-for-collection context concept-id :service))
   "tools" (map #(umm-json/umm->json %)
                (get-associations-for-collection context concept-id :tool))})

(defn render-collection
  "Renders a UMM-C collection record and returns the HTML as a string."
  ([context collection concept-id]
   (render-collection
    context
    collection
    concept-id
    (get-additional-information context concept-id)))
  ([context collection concept-id additional-information]
   (let [umm-json (umm-json/umm->json
                   (vm/migrate-umm context
                                   :collection
                                   umm-version/current-collection-version
                                   (context->preview-gem-umm-version context)
                                   collection))]
    (render-erb (context->jruby-runtime context)
                collection-preview-erb
                ;; Arguments for collection preview. See the ERB file for documentation.
                {"umm_json" umm-json
                 "relative_root_url" (context->relative-root-url context)
                 "edsc_url" (search-edsc-url)
                 "concept_id" concept-id
                 "additional_information" additional-information
                 "search_public_root_url" (config/application-public-root-url context)}))))
