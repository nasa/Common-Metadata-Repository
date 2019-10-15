(ns cmr.ingest.api.translation
  "Defines an API for translating metadata between formats."
  (:require
   [clojure.java.io :as io]
   [clojure.test.check.generators :as test-check-gen]
   [cmr.common.cache :as cache]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.xml.xslt :as xslt]
   [cmr.umm-spec.legacy :as umm-legacy]
   [cmr.umm-spec.test.umm-generators :as umm-generators]
   [cmr.umm-spec.umm-spec-core :as umm-spec]
   [cmr.umm-spec.util :as util]
   [cmr.umm-spec.versioning :as versioning]
   [compojure.core :refer :all]))

(def concept-type->supported-input-formats
  "A map of concept type to the list of formats that are supported for input of translation."
  {:collection #{mt/echo10
                 mt/umm-json
                 mt/iso19115
                 mt/dif
                 mt/dif10
                 mt/iso-smap}
   :granule #{mt/echo10
              mt/umm-json
              mt/iso-smap}})

(def concept-type->supported-output-formats
  "A map of concept type to the list of formats that are supported for output of translation."
  {:collection #{mt/echo10
                 mt/umm-json
                 mt/iso19115
                 mt/dif
                 mt/dif10
                 mt/iso-smap}
   :granule #{mt/echo10
              mt/umm-json
              mt/iso-smap
              mt/iso19115}})

(defn- umm-errors
  "Returns a seq of errors found by UMM validation."
  [context concept-type umm]
  (seq
   (umm-legacy/validate-metadata concept-type
                                 :umm-json
                                 (umm-spec/generate-metadata context umm :umm-json))))

(def xsl-transformer-cache-name
  "This is the name of the cache to use for XSLT transformer templates. Templates are thread
  safe but transformer instances are not."
  :xsl-transformer-templates)

(def ^:private echo10-iso19115-xslt
  (io/resource "xslt/echo10_to_iso19115.xsl"))

(defn- get-template
  "Returns a XSLT template from the filename, using the context cache."
  [context filename]
  (cache/get-value
   (cache/context->cache context xsl-transformer-cache-name)
   filename
   #(xslt/read-template filename)))

(defn- generate-granule-iso-mends-metadata
  "Generate granule ISO MENDS metadata from the given umm-lib granule model.
  CMR does this by generating granule metadata in ECHO10 format and then uses xslt to convert
  the ECHO10 metadata into ISO MENDS."
  [context umm]
  (let [echo10-metadata (umm-legacy/generate-metadata context umm :echo10)]
    (xslt/transform echo10-metadata (get-template context echo10-iso19115-xslt))))

(defn- generate-metadata
  "Generate the metadata for the given umm record and output format."
  [context umm output-format]
  (if (and (= cmr.umm.umm_granule.UmmGranule (type umm))
           (= :iso19115 output-format))
    (generate-granule-iso-mends-metadata context umm)
    (umm-legacy/generate-metadata context umm output-format)))

(defn- translate-response
  "Returns a translate API response map for the given UMM record and the requested response media type."
  [context umm requested-media-type]
  ;; We are only going to respond with a version parameter if we are returning UMM JSON.
  (let [response-media-type (if (mt/umm-json? requested-media-type)
                              (versioning/with-default-version
                               (:concept-type umm) requested-media-type)
                              requested-media-type)
        body (generate-metadata context umm (mt/mime-type->format response-media-type))]
    {:status  200
     :body    body
     :headers {"Content-Type" response-media-type}}))

(defmulti perform-translation
  "Perform the collection translation and return the translation response map."
  (fn [context concept-type content-type accept-header body skip-umm-validation options]
    concept-type))

(defmethod perform-translation :collection
  [context concept-type content-type accept-header body skip-umm-validation options]
  (let [umm (umm-spec/parse-metadata context concept-type content-type body options)]
    (if-let [umm-errors (when-not skip-umm-validation (umm-errors context concept-type umm))]
      (errors/throw-service-errors :invalid-data umm-errors)
      ;; Otherwise, if the parsed UMM validates, return a response with the metadata in the
      ;; requested format.
      (translate-response context umm accept-header))))

(defmethod perform-translation :granule
  [context concept-type content-type accept-header body skip-umm-validation options]
  (let [umm (umm-legacy/parse-concept
             context {:concept-type concept-type
                      :format content-type
                      :metadata body})]
    (if-let [umm-errors (when-not skip-umm-validation (umm-errors context concept-type umm))]
      (errors/throw-service-errors :invalid-data umm-errors)
      (translate-response context umm accept-header))))

(defn- translate
  "Fulfills the translate request using the body, content type header, and accept header. Returns
  a ring response with translated metadata."
  [context concept-type headers body skip-umm-validation]
  (let [supported-input-formats (concept-type->supported-input-formats concept-type)
        supported-output-formats (concept-type->supported-output-formats concept-type)
        content-type (get headers "content-type")
        ;; CMR-5495 If we do not get an accept header, we would like to show the user an error
        ;; describing the problem.
        accept-header (or (get headers "accept") "")
        ;; always skip sanitize of granules for now as we have not considered sanitizing for granules yet
        skip-sanitize-umm? (= "true" (get headers "cmr-skip-sanitize-umm-c"))
        options (if (and skip-sanitize-umm? (mt/umm-json? accept-header))
                  util/skip-sanitize-parsing-options
                  util/default-parsing-options)]

    ;; just for validation (throws service error if invalid media type is given)
    (mt/extract-header-mime-type supported-input-formats headers "content-type" true)
    (mt/extract-header-mime-type
     supported-output-formats
     ;; CMR-5495 Put "" string back into the accept header if none is supplied to give proper
     ;; error message.
     (assoc headers "accept" accept-header)
     "accept"
     true)

    ;; Can not skip-sanitize-umm when the target format is not UMM-C
    (when (and skip-sanitize-umm? (not (mt/umm-json? accept-header)))
      (let [errors ["Skipping santization during translation is only supported when the target format is UMM-C"]]
        (errors/throw-service-errors :bad-request errors)))

    ;; Validate the input data against its own native schema (ECHO, DIF, etc.)
    (if-let [errors (seq (umm-legacy/validate-metadata concept-type content-type body))]
      (errors/throw-service-errors :bad-request errors)

      ;; If there were no errors, then proceed to convert it to UMM and check for UMM schema
      ;; validation errors.
      (perform-translation
       context concept-type content-type accept-header body skip-umm-validation options))))

(def translation-routes
  (context "/translate" []
    (POST "/collection" {:keys [body headers request-context params]}
      (translate request-context :collection headers (slurp body)
                 (= "true" (:skip_umm_validation params))))
    (POST "/granule" {:keys [body headers request-context params]}
      (translate request-context :granule headers (slurp body)
                 (= "true" (:skip_umm_validation params))))))

(def random-metadata-routes
  "This defines routes for development purposes that can generate random metadata and return it."
  (GET "/random-metadata" {:keys [body headers request-context]}
    (let [supported-formats (concept-type->supported-output-formats :collection)
          output-mime-type (mt/extract-header-mime-type supported-formats headers "accept" true)]

      (let [umm (test-check-gen/generate umm-generators/umm-c-generator)
            output-str (umm-spec/generate-metadata umm output-mime-type)]
        {:status 200
         :body output-str
         :headers {"Content-Type" output-mime-type}}))))
