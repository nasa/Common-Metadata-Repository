(ns cmr.search.data.metadata-retrieval.metadata-transformer
  "Contains functions for converting concept metadata into other formats."
  (:require [clojure.java.io :as io]
            [cmr.common.xml.xslt :as xslt]
            [cmr.common.util :as u]
            [cmr.common.xml :as cx]
            [cmr.common.cache :as cache]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.mime-types :as mt]
            [cmr.common-app.services.search.query-model :as qm]

            ;; UMM library
            [cmr.umm.umm-core :as umm-lib-core]

            ;; UMM Spec
            [cmr.umm-spec.versioning :as ver]
            [cmr.umm-spec.umm-spec-core :as umm-spec]
            [cmr.umm-spec.umm-json :as umm-json]
            [cmr.common.services.errors :as errors]

            ;; render collections as html
            [cmr.collection-renderer.services.collection-renderer :as collection-renderer]))

(def transformer-supported-base-formats
  "The set of formats supported by the transformer."
  #{:echo10 :dif :dif10 :iso19115 :umm-json :iso-smap})

(defn transformer-supported-format?
  "Returns true if the transformer supports transforming to this format"
  [result-format]
  (contains? transformer-supported-base-formats (qm/base-result-format result-format)))

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

;; dynamic is here only for testing purposes to test failure cases.
(defn ^:dynamic transform-strategy
  "Determines which transformation strategy should be used to convert the given concept to the target
   format"
  [concept target-format]
  ;;throw exception if target format is native. That should be handled elsewhere.
  {:pre [(not= :native target-format)]}

  (let [concept-mime-type (:format concept)]
    (cond
      ;; No conversion is required
      (= (mt/mime-type->format concept-mime-type) target-format)
      :current-format

      ;; Use XSLT
      (types->xsl [(mt/mime-type->format concept-mime-type) target-format])
      :xslt

      (= :html target-format)
      :html

      ;; only granule uses umm-lib
      (= :granule (:concept-type concept))
      :umm-lib

      :else
      :umm-spec)))

(defmulti transform-with-strategy
  "Transforms the concept into the set of target formats specified using the given strategy"
  (fn [context concept strategy target-formats]
    strategy))

(defmethod transform-with-strategy :default
  [_ concept strategy target-formats]
  (errors/internal-error!
   (format "Unexpected transform strategy [%s] from concept of type [%s] to [%s]"
           strategy (:format concept) (pr-str target-formats))))

(defmethod transform-with-strategy :current-format
  [context concept _ _]
  {(mt/mime-type->format (:format concept))
   (cx/remove-xml-processing-instructions (:metadata concept))})

(defmethod transform-with-strategy :xslt
  [context concept _ target-formats]
  (let [{concept-mime-type :format, metadata :metadata} concept]
    (reduce (fn [translated-map target-format]
              (let [xsl (types->xsl [(mt/mime-type->format concept-mime-type) target-format])]
                (assoc translated-map target-format
                       (cx/remove-xml-processing-instructions
                        (xslt/transform metadata (get-template context xsl))))))
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
  "Transforms the concept into multiple different formats. Returns a map of target format to metadata."
  [context concept target-formats ignore-exceptions?]
  {:pre [(not (:deleted concept))]}
  (->> target-formats
       (group-by #(transform-strategy concept %))
       (keep (fn [[k v]]
               (if ignore-exceptions?
                 (try
                   (transform-with-strategy context concept k v)
                   (catch Throwable e
                     (log/error
                      e
                      (str "Ignoring exception while trying to transform metadata for concept "
                           (:concept-id concept) " with revision " (:revision-id concept) " error: "
                           (.getMessage e)))))
                 (transform-with-strategy context concept k v))))
       (reduce into {})))

(defn transform
  "Transforms a concept to the target format given returning metadata."
  [context concept target-format]
  {:pre [(not (:deleted concept))]}
  (if (= target-format :native)
    (:metadata concept)
    (let [strategy (transform-strategy concept target-format)
          target-format-result-map (transform-with-strategy
                                    context concept strategy [target-format])]
      (get target-format-result-map target-format))))

(defn transform-concepts
  "Transforms concepts to the given format returning an updated concept. Handles deleted concepts."
  [context concepts target-format]
  (if (= :native target-format)
    concepts
    (u/fast-map (fn [concept]
                  (if (:deleted concept)
                    ;; A deleted concept needs no transformation.
                    (assoc concept
                           :format (mt/format->mime-type target-format))
                    (assoc concept
                           :format (mt/format->mime-type target-format)
                           :metadata (transform context concept target-format))))
                concepts)))
