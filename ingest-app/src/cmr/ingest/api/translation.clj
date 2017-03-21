(ns cmr.ingest.api.translation
  "Defines an API for translating metadata between formats."
  (:require
    [clojure.test.check.generators :as test-check-gen]
    [cmr.common.mime-types :as mt]
    [cmr.common.services.errors :as errors]
    [cmr.umm-spec.test.umm-generators :as umm-generators]
    [cmr.umm-spec.versioning :as ver]
    [cmr.umm-spec.umm-spec-core :as umm-spec]
    [cmr.umm-spec.util :as u]
    [compojure.core :refer :all]))

(def concept-type->supported-formats
  "A map of concept type to the list of formats that are supported both for input and output of
  translation."
  {:collection #{mt/echo10
                 mt/umm-json
                 mt/iso19115
                 mt/dif
                 mt/dif10
                 mt/iso-smap}})

(defn- umm-errors
  "Returns a seq of errors found by UMM validation."
  [context concept-type umm]
  (seq
    (umm-spec/validate-metadata concept-type
                                :umm-json
                                (umm-spec/generate-metadata context umm :umm-json))))

(defn translate-response
  "Returns a translate API response map for the given UMM record and the requested response media type."
  [context umm requested-media-type]
  ;; We are only going to respond with a version parameter if we are returning UMM JSON.
  (let [response-media-type (if (mt/umm-json? requested-media-type)
                              (ver/with-default-version requested-media-type)
                              requested-media-type)
        body (umm-spec/generate-metadata context umm response-media-type)]
    {:status  200
     :body    body
     :headers {"Content-Type" response-media-type}}))

(defn translate
  "Fulfills the translate request using the body, content type header, and accept header. Returns
  a ring response with translated metadata."
  [context concept-type headers body skip-umm-validation]
  (let [supported-formats (concept-type->supported-formats concept-type)
        content-type (get headers "content-type")
        accept-header (get headers "accept")
        skip-sanitize-umm? (= "true" (get headers "cmr-skip-sanitize-umm-c"))
        options (if (and skip-sanitize-umm? (mt/umm-json? accept-header))
                  u/skip-sanitize-parsing-options
                  u/default-parsing-options)]

    ;; just for validation (throws service error if invalid media type is given)
    (mt/extract-header-mime-type supported-formats headers "content-type" true)
    (mt/extract-header-mime-type supported-formats headers "accept" true)

    ;; Can not skip-sanitize-umm when the target format is not UMM-C 
    (when (and skip-sanitize-umm? (not (mt/umm-json? accept-header)))
      (let [errors ["Skipping santization during translation is only supported when the target format is UMM-C"]]
        (errors/throw-service-errors :bad-request errors))) 
   
    ;; Validate the input data against its own native schema (ECHO, DIF, etc.)
    (if-let [errors (seq (umm-spec/validate-metadata concept-type content-type body))]
      (errors/throw-service-errors :bad-request errors)

      ;; If there were no errors, then proceed to convert it to UMM and check for UMM schema
      ;; validation errors.
      (let [umm (umm-spec/parse-metadata context concept-type content-type body options)]
        (if-let [umm-errors (when-not skip-umm-validation (umm-errors context concept-type umm))]
          (errors/throw-service-errors :invalid-data umm-errors)
          ;; Otherwise, if the parsed UMM validates, return a response with the metadata in the
          ;; requested XML format.
          (translate-response context umm accept-header))))))

(def translation-routes
  (context "/translate" []
    (POST "/collection" {:keys [body headers request-context params]}
          (translate request-context :collection headers (slurp body)
                     (= "true" (:skip_umm_validation params))))))

;; NOTE: Granule translation is not supported yet. This will be done when granules are added to the UMM spec.

(def random-metadata-routes
  "This defines routes for development purposes that can generate random metadata and return it."
  (GET "/random-metadata" {:keys [body headers request-context]}
    (let [supported-formats (concept-type->supported-formats :collection)
          output-mime-type (mt/extract-header-mime-type supported-formats headers "accept" true)
          output-format (mt/mime-type->format output-mime-type)]

      (let [umm (test-check-gen/generate umm-generators/umm-c-generator)
            output-str (umm-spec/generate-metadata umm output-mime-type)]
        {:status 200
         :body output-str
         :headers {"Content-Type" output-mime-type}}))))
