(ns cmr.search.data.metadata-retrieval.metadata-transformer
  "Contains functions for converting concept metadata into other formats."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [cmr.common.services.search.query-model :as qm]
   [cmr.common.cache :as cache]
   [cmr.common.concepts :as cs]
   [cmr.common.log :as log :refer (info debug)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as u]
   [cmr.common.xml :as cx]
   [cmr.common.xml.xslt :as xslt]
   [cmr.umm-spec.legacy :as legacy]
   [cmr.umm-spec.migration.version.core :as vm]
   [cmr.umm-spec.umm-json :as umm-json]
   [cmr.umm-spec.umm-spec-core :as umm-spec]))

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

;; dynamic is here only for testing purposes to test failure cases.
(defn ^:dynamic transform-strategy
  "Determines which transformation strategy should be used to convert the given concept to the target
   format"
  [concept target-format]
  ;;throw exception if target format is native. That should be handled elsewhere.
  {:pre [(not= :native target-format)]}

  (let [concept-mime-type (:format concept)]
    (cond
      ;; No conversion is required - same format and version.
      (= (mt/mime-type->format concept-mime-type) target-format)
      :current-format

      ;; Use XSLT
      (and (= :granule (:concept-type concept))
           (types->xsl [(mt/mime-type->format concept-mime-type) target-format]))
      :xslt

      (= :html target-format)
      :html

      (and (= :granule (:concept-type concept))
           (= :umm-json (mt/format-key concept-mime-type))
           (= :iso19115 target-format))
      :granule-umm-g-to-iso

      (and (= :umm-json (mt/format-key concept-mime-type))
           (= :umm-json (mt/format-key target-format)))
      :migrate-umm-json

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

(defmethod transform-with-strategy :umm-spec
  [context concept _ target-formats]
  (let [{concept-mime-type :format, metadata :metadata} concept
        ummc (umm-spec/parse-metadata context (:concept-type concept) concept-mime-type metadata)]
    (reduce (fn [translated-map target-format]
              (assoc translated-map target-format
                     (umm-spec/generate-metadata context ummc target-format)))
            {}
            target-formats)))

(defmethod transform-with-strategy :umm-lib
  [context concept _ target-formats]
  (let [{concept-mime-type :format, metadata :metadata} concept
        [t1 umm] (u/time-execution (legacy/parse-concept context concept))
        [t2 result] (u/time-execution (reduce (fn [translated-map target-format]
                                                (assoc translated-map target-format
                                                       (legacy/generate-metadata context umm target-format)))
                                              {}
                                              target-formats))]
    (debug "transform-with-strategy umm-lib: "
          "legacy/parse-concept time: " t1
          "reduce w/ legacy/generate-metadata time: " t2
          "concept-mime-type: " concept-mime-type
          "parent request num-concepts: " (:num-concepts concept)
          "target-formats: " target-formats
          "provider: " (:provider-id concept)
          "metadata length: " (count metadata))
    result))

(defmethod transform-with-strategy :granule-umm-g-to-iso
  [context concept _ target-formats]
  (let [umm (legacy/parse-concept context concept)
        echo10-metadata (legacy/generate-metadata context umm :echo10)]
    (assert (= [:iso19115] target-formats))
    (reduce (fn [translated-map target-format]
              (let [xsl (types->xsl [:echo10 target-format])]
                (assoc translated-map target-format
                       (cx/remove-xml-processing-instructions
                        (xslt/transform echo10-metadata (get-template context xsl))))))
            {}
            target-formats)))

(defmethod transform-with-strategy :migrate-umm-json
  [context concept _ target-formats]
  (let [{concept-mime-type :format, metadata :metadata, concept-type :concept-type} concept
        source-version (umm-spec/umm-json-version concept-type concept-mime-type)
        [t result] (u/time-execution (reduce (fn [translated-map target-format]
                                               (assoc translated-map target-format
                                                      (umm-json/umm->json
                                                       (u/remove-nils-empty-maps-seqs
                                                        (vm/migrate-umm context
                                                                        concept-type
                                                                        source-version
                                                                        (umm-spec/umm-json-version concept-type
                                                                                                   target-format)
                                                                        (json/decode metadata true))))))
                                             {}
                                             target-formats))]
    (debug "transform-with-strategy migrate-umm-json: "
          "time: " t
          "concept-mime-type: " concept-mime-type
          "concept-type: " concept-type
          "parent request num-concepts: " (:num-concepts concept)
          "target-formats: " target-formats
          "source version: " source-version
          "provider: " (:provider-id concept)
          "metadata length: " (count metadata))
    result))

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
      (debug (format "transform: concept: [%s] target-format: [%s] transform-strategy: [%s]"
                    (:concept-id concept)
                    target-format
                    strategy))
      (get target-format-result-map target-format))))

(defn transform-concepts
  "Transforms concepts to the given format returning an updated concept. Handles deleted concepts."
  [context concepts target-format]
  (if (= :native target-format)
    concepts
    (u/fast-map (fn [concept]
                  (if (or (:deleted concept)
                          (cs/generic-concept? (:concept-type concept)))
                    ;; A deleted or generic concept needs no transformation.
                    (assoc concept
                           :format (mt/format->mime-type target-format))
                    (assoc concept
                           :format (mt/format->mime-type target-format)
                           :metadata (transform context
                                                (assoc concept :num-concepts (count concepts))
                                                target-format))))
                concepts)))
