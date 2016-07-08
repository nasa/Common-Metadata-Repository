(ns cmr.search.data.metadata-retrieval.metadata-transformer
  "TODO"
  (:require [clojure.java.io :as io]
            [cmr.common.xml.xslt :as xslt]
            [cmr.common.cache :as cache]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.mime-types :as mt]
            [cmr.common-app.services.search.query-model :as qm]
            [cmr.search.services.result-format-helper :as rfh]

            ;; UMM library
            [cmr.umm.core :as umm-lib-core]

            ;; UMM Spec
            [cmr.umm-spec.versioning :as ver]
            [cmr.umm-spec.core :as umm-spec]
            [cmr.umm-spec.umm-json :as umm-json]
            [cmr.common.services.errors :as errors]

            ;; render collections as html
            [cmr.collection-renderer.services.collection-renderer :as collection-renderer]))

;; TODO unit test this namespace

(def types->xsl
  "Defines the [metadata-format target-format] to xsl mapping"
  {[:echo10 :iso19115] (io/resource "xslt/echo10_to_iso19115.xsl")})

(def xsl-transformer-cache-name
  "This is the name of the cache to use for XSLT transformer templates. Templates are thread
  safe but transformer instances are not.
  http://www.onjava.com/pub/a/onjava/excerpt/java_xslt_ch5/?page=9"
  :xsl-transformer-templates)

(defn- get-template
  "Returns a XSLT template from the filename, using the context cache."
  [context f]
  (cache/get-value
    (cache/context->cache context xsl-transformer-cache-name)
    f
    #(xslt/read-template f)))

(defn- generate-html-response
  "Returns an HTML representation of the collection concept."
  [context concept]
  (let [collection (umm-spec/parse-metadata
                     context :collection (:format concept) (:metadata concept))]
    (collection-renderer/render-collection context collection)))

;; TODO consider memoizing this function but with concept-mime-type as first arg
(defn transform-strategy
  "TODO"
  [concept target-format]
  ;;throw exception if target format is native. That should be handled elsewhere.
  {:pre [(not= :native target-format)]}

  (let [concept-mime-type (:format concept)]
    (cond
      ;; No conversion is required
      (= (rfh/mime-type->search-result-format concept-mime-type) target-format)
      :current-format

      ;; Use XSLT
      (types->xsl [(mt/mime-type->format concept-mime-type) target-format])
      :xslt

      (= :html target-format)
      :html

      ;; UMM JSON is the desired response or it's coming from UMM JSON
      (or (mt/umm-json? concept-mime-type)
          (= :umm-json (qm/base-result-format target-format)))
      :umm-spec

      ;; Going from XML metadata to some otner XML metadata.
      ;; Use UMM lib (for now for collections)
      :else
      :umm-lib)))

;; TODO move this to separately named functions and use a cond to call the appropriate function
;; Getting rid of multimethod will help performance

(defmulti transform-with-strategy
  "TODO"
  (fn [context concept strategy target-formats]
    strategy))

(defmethod transform-with-strategy :default
  [_ concept strategy target-formats]
  (errors/internal-error!
   (format "Unexpected transform strategy [%s] from concept of type [%s] to [%s]"
           strategy (:format concept) (pr-str target-formats))))

(defmethod transform-with-strategy :current-format
  [context concept _ _]
  {(rfh/mime-type->search-result-format (:format concept)) (:metadata concept)})

(defmethod transform-with-strategy :xslt
  [context concept _ target-formats]
  (let [{concept-mime-type :format, metadata :metadata} concept]
    (reduce (fn [translated-map target-format]
              (let [xsl (types->xsl [(mt/mime-type->format concept-mime-type) target-format])]
                (assoc translated-map target-format
                       (xslt/transform metadata (get-template context xsl)))))
            {}
            target-formats)))

(defmethod transform-with-strategy :html
  [context concept _ _]
  {:html (generate-html-response context concept)})

(defmethod transform-with-strategy :umm-spec
  [context concept _ target-formats]
  (let [{concept-mime-type :format, metadata :metadata} concept
        ummc (umm-spec/parse-metadata context :collection concept-mime-type metadata)]
    (reduce (fn [translated-map target-format]
              (assoc translated-map target-format
                     (umm-spec/generate-metadata context ummc target-format)))
            {}
            target-formats)))

(defmethod transform-with-strategy :umm-lib
  [context concept _ target-formats]
  (let [{concept-mime-type :format, metadata :metadata} concept
        umm (umm-lib-core/parse-concept concept)]
    (reduce (fn [translated-map target-format]
              (assoc translated-map target-format
                     (umm-lib-core/umm->xml umm target-format)))
            {}
            target-formats)))

(defn transform-to-multiple-formats
  "TODO"
  [context concept target-formats]
  (->> target-formats
       (group-by #(transform-strategy concept %))
       (map #(transform-with-strategy context concept (key %) (val %)))
       (reduce into {})))

(defn transform
  "TODO"
  [context concept target-format]
  (let [strategy (transform-strategy concept target-format)
        target-format-result-map (transform-with-strategy
                                  context concept strategy [target-format])]
    (get target-format-result-map target-format)))
