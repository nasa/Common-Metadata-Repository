(ns cmr.search.data.metadata-retrieval.metadata-transformer
  "TODO"
  (:require [clojure.java.io :as io]
            [cmr.common.xml.xslt :as xslt]
            [cmr.common.cache :as cache]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.mime-types :as mt]
            [cmr.common-app.services.search.query-model :as qm]

            ;; UMM library
            [cmr.umm.core :as ummc]

            ;; UMM Spec
            [cmr.umm-spec.versioning :as ver]
            [cmr.umm-spec.core :as umm-spec]
            [cmr.umm-spec.umm-json :as umm-json]
            [cmr.common.services.errors :as errors]

            ;; render collections as html
            [cmr.collection-renderer.services.collection-renderer :as collection-renderer]))

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

(defn transform
  "TODO"
  [context concept target-format]
  (let [{concept-mime-type :format
         metadata :metadata} concept]
   (if-let [xsl (types->xsl [(mt/mime-type->format concept-mime-type) target-format])]

     ; xsl is defined for the transformation, so use xslt
     (xslt/transform metadata (get-template context xsl))

     ;; No XSLT is defined. Use UMM libraries to convert
     (cond
       ;; HTML is desired response
       (= :html target-format)
       (generate-html-response context concept)

       ;; UMM JSON is the desired response
       (mt/umm-json? concept-mime-type)
       (if (and (= :umm-json (qm/base-result-format target-format))
                (= (or (mt/version-of concept-mime-type) ver/current-version)
                   (or (:version target-format) ver/current-version)))
         ;; The metadata is in the same version of UMM JSON as requested by the user.
         metadata
         ;; The user has requested a different format for the metadata.
         ;; Use UMM Spec to parse it and generate metadata in the desired format.
         (umm-spec/generate-metadata
          context
          (umm-spec/parse-metadata context :collection concept-mime-type metadata)
          target-format))

       ;; Going from UMM-JSON to some other format (Use UMM Spec)
       (= :umm-json (qm/base-result-format target-format))
       (umm-json/umm->json
        (umm-spec/parse-metadata context :collection concept-mime-type metadata))

       ;; Going from XML metadata to some otner XML metadata.
       ;; Use UMM lib (for now)
       :else
       (-> concept
           ummc/parse-concept
           (ummc/umm->xml target-format))))))
