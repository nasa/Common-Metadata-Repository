(ns cmr.ingest.validation.validation
  "Provides functions to validate concept"
  (:require
    [clojure.string :as str]
    [cmr.common-app.services.kms-fetcher :as kms-fetcher]
    [cmr.common-app.services.kms-lookup :as kms-lookup]
    [cmr.common.log :as log :refer (warn)]
    [cmr.common.mime-types :as mt]
    [cmr.common.services.errors :as errors]
    [cmr.common.validations.core :as v]
    [cmr.ingest.config :as config]
    [cmr.ingest.services.humanizer-alias-cache :as humanizer-alias-cache]
    [cmr.ingest.services.messages :as msg]
    [cmr.ingest.validation.business-rule-validation :as bv]
    [cmr.umm-spec.json-schema :as json-schema]
    [cmr.umm-spec.umm-json :as umm-json]
    [cmr.umm-spec.umm-spec-core :as umm-spec]
    [cmr.umm-spec.validation.umm-spec-validation-core :as umm-spec-validation]
    [cmr.umm.umm-core :as umm]
    [cmr.umm.validation.validation-core :as umm-validation]))

(def ^:private
  valid-concept-mime-types
  {:collection #{mt/echo10 mt/iso-smap mt/iso19115 mt/dif mt/dif10 mt/umm-json}
   :granule    #{mt/echo10 mt/iso-smap}})


(defn- validate-format
  "Validates the format of the concept. Throws a 415 error if invalid."
  [concept]
  (let [content-type (mt/base-mime-type-of (:format concept))
        valid-types (get valid-concept-mime-types (:concept-type concept))]
    (when (and (= mt/umm-json content-type)
               (not (mt/version-of (:format concept))))
      (errors/throw-service-error :invalid-content-type
                                  (format "Missing version parameter from Content-Type header.")))
    (when-not (contains? valid-types content-type)
      (errors/throw-service-error :invalid-content-type
                                  (format "Invalid content-type: %s. Valid content-types: %s."
                                          content-type (str/join ", " valid-types))))))
(defn- validate-metadata-length
  "Validates the metadata length is not unreasonable."
  [concept]
  (when (<= (count (:metadata concept)) 4)
    (errors/throw-service-error :bad-request "XML content is too short.")))

(defn validate-concept-request
  "Validates the initial request to ingest a concept."
  [concept]
  (validate-format concept)
  (validate-metadata-length concept))

(defn if-errors-throw
  "Throws an error if there are any errors."
  [error-type errors]
  (when (seq errors)
    (errors/throw-service-errors error-type errors)))

(defn match-kms-keywords-validation
  "A validation that checks that the item matches a known KMS field. Takes the following arguments:

  * kms-index - The keywords map as returned by kms-fetcher/get-kms-index
  * matches-keyword-fn - A function that will take the kms-index and the value and return a
  logically true value if the value matches a keyword.
  * msg-fn - A function taking the value and returning the error to return to the user if it doesn't
  match."
  [kms-index keyword-scheme msg-fn]
  (v/every
    (fn [field-path value]
      (when-not (kms-lookup/lookup-by-umm-c-keyword kms-index keyword-scheme value)
        {field-path [(msg-fn value)]}))))

(defn keyword-validations
  "Creates validations that check various collection fields to see if they match KMS keywords."
  [context]
  (let [kms-index (kms-fetcher/get-kms-index context)]
    {:Platforms [(match-kms-keywords-validation
                  kms-index :platforms msg/platform-not-matches-kms-keywords)
                 (v/every {:Instruments (match-kms-keywords-validation
                                         kms-index :instruments
                                         msg/instrument-not-matches-kms-keywords)})]
     :ScienceKeywords (match-kms-keywords-validation
                         kms-index :science-keywords msg/science-keyword-not-matches-kms-keywords)
     :Projects (match-kms-keywords-validation
                kms-index :projects msg/project-not-matches-kms-keywords)}))

(defn validate-concept-metadata
  [concept]
  (if-errors-throw :bad-request
                   (if (mt/umm-json? (:format concept))
                     (let [umm-version (mt/version-of (:format concept))
                           accept-version (config/ingest-accept-umm-version)]
                       (if (>= 0 (compare umm-version accept-version))
                         (umm-spec/validate-metadata (:concept-type concept)
                                                     (:format concept)
                                                     (:metadata concept))
                         [(str "UMM JSON version " accept-version  " or lower can be ingested. "
                               "Any version above that is considered in-development "
                               "and cannot be ingested at this time.")]))
                     (umm/validate-concept-xml concept))))

(defn validate-collection-umm-spec-schema
  "Validate the collection against the JSON schema and throw errors if configured or return
  a list of warnings"
  [collection validation-options]
  (if-let [err-messages (seq (json-schema/validate-umm-json
                              (umm-json/umm->json collection)
                              :collection))]
    (if (or (:validate-umm? validation-options) (config/return-umm-json-validation-errors))
      (errors/throw-service-errors :invalid-data err-messages)
      (do
        (warn "UMM-C JSON-Schema Validation Errors: " (pr-str (vec err-messages)))
        err-messages))))

(defn umm-spec-validate-collection
  "Validate collection through umm-spec validation functions. If warn? flag is
  true and umm-spec-validation is off, log warnings and return messages, otherwise throw errors."
  [collection validation-options context warn?]
  (when-let [err-messages (seq (umm-spec-validation/validate-collection
                                collection
                                (when (:validate-keywords? validation-options)
                                  [(keyword-validations context)])))]
    (if (or (:validate-umm? validation-options) (config/return-umm-spec-validation-errors)
            (not warn?))
     (errors/throw-service-errors :invalid-data err-messages)
     (do
      (warn "UMM-C UMM Spec Validation Errors: " (pr-str (vec err-messages)))
      err-messages))))

(defn umm-spec-validate-collection-warnings
 "Validate umm-spec collection validation warnings functions - errors that we want
 to report but we do not want to fail ingest."
 [collection validation-options context]
 (when-let [err-messages (seq (umm-spec-validation/validate-collection-warnings
                               collection))]
   (if (or (:validate-umm? validation-options) (config/return-umm-spec-validation-errors))
     (errors/throw-service-errors :invalid-data err-messages)
     (do
      (warn "UMM-C UMM Spec Validation Errors: " (pr-str (vec err-messages)))
      err-messages))))

(defn validate-granule-umm-spec
  "Validates a UMM granule record using rules defined in UMM Spec with a UMM Spec collection record,
   updated with platform aliases whoes shortnames don't exist in the platforms."
  [context collection granule]
  (when-let [errors (seq (umm-spec-validation/validate-granule
                           (humanizer-alias-cache/update-collection-with-aliases
                             context collection true)
                           granule))]
    (if-errors-throw :invalid-data errors)))

(defn validate-granule-umm
  "Validates a UMM granule record using rules defined in UMM with a UMM collection record,
   updated with platform aliases whoes shortnames don't exist in the platforms."
  [context collection granule]
    (if-errors-throw :invalid-data (umm-validation/validate-granule
                                     (humanizer-alias-cache/update-collection-with-aliases
                                       context collection false)
                                     granule)))

(defn validate-business-rules
  "Validates the concept against CMR ingest rules."
  [context concept]
  (if-errors-throw :invalid-data
                   (mapcat #(% context concept)
                           (bv/business-rule-validations (:concept-type concept)))))
