(ns cmr.ingest.validation.validation
  "Provides functions to validate concept"
  (:require [clojure.string :as s]
            [cmr.common.services.errors :as errors]
            [cmr.common.mime-types :as mt]
            [cmr.common.log :as log :refer (warn)]
            [cmr.umm.umm-core :as umm]
            [cmr.ingest.config :as config]
            [cmr.umm.validation.validation-core :as umm-validation]
            [cmr.umm-spec.umm-spec-core :as umm-spec]
            [cmr.umm-spec.validation.umm-spec-validation-core :as umm-spec-validation]
            [cmr.umm-spec.umm-json :as umm-json]
            [cmr.umm-spec.json-schema :as json-schema]
            [cmr.ingest.validation.business-rule-validation :as bv]
            [cmr.common.validations.core :as v]
            [cmr.common-app.services.kms-fetcher :as kms-fetcher]
            [cmr.ingest.services.messages :as msg]))

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
                                          content-type (s/join ", " valid-types))))))
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

  * gcmd-keywords-map - The keywords map as returned by kms-fetcher/get-gcmd-keywords-map
  * matches-keyword-fn - A function that will take the gcmd-keywords-map and the value and return a
  logically true value if the value matches a keyword.
  * msg-fn - A function taking the value and returning the error to return to the user if it doesn't
  match."
  [gcmd-keywords-map matches-keyword-fn msg-fn]
  (v/every
    (fn [field-path value]
      (when-not (matches-keyword-fn gcmd-keywords-map value)
        {field-path [(msg-fn value)]}))))

(defn keyword-validations
  "Creates validations that check various collection fields to see if they match KMS keywords."
  [context]
  (let [gcmd-keywords-map (kms-fetcher/get-gcmd-keywords-map context)]
    {:platforms [(match-kms-keywords-validation
                   gcmd-keywords-map
                   kms-fetcher/get-full-hierarchy-for-platform
                   msg/platform-not-matches-kms-keywords)
                 (v/every {:instruments (match-kms-keywords-validation
                                          gcmd-keywords-map
                                          kms-fetcher/get-full-hierarchy-for-instrument
                                          msg/instrument-not-matches-kms-keywords)})]
     :science-keywords (match-kms-keywords-validation
                         gcmd-keywords-map
                         kms-fetcher/get-full-hierarchy-for-science-keyword
                         msg/science-keyword-not-matches-kms-keywords)
     :projects (match-kms-keywords-validation
                 gcmd-keywords-map
                 kms-fetcher/get-full-hierarchy-for-project
                 msg/project-not-matches-kms-keywords)}))

(defn validate-concept-metadata
  [concept]
  (if-errors-throw :bad-request
                   (if (mt/umm-json? (:format concept))
                     (umm-spec/validate-metadata (:concept-type concept)
                                                 (:format concept)
                                                 (:metadata concept))
                     (umm/validate-concept-xml concept))))

(defn validate-collection-umm
  [context collection validate-keywords?]
  ;; Log any errors from the keyword validation if we are not returning them to the client.
  ;; Move the keyword validations to validate-collection-umm-spec when this function is removed
  (when-not validate-keywords?
    (when-let [errors (seq (v/validate (keyword-validations context) collection))]
      (warn (format "Collection with entry title [%s] had the following keyword validation errors: %s"
                    (:entry-title collection) (pr-str errors)))))
  ;; Validate the collection and throw errors that will be sent to the client.
  (if-errors-throw :invalid-data (umm-validation/validate-collection
                                   collection
                                   (when validate-keywords?
                                     [(keyword-validations context)]))))

(defn validate-collection-umm-spec
  "Validate UMM-C record"
  [context collection validation-options]
  ;; Add keyword validations from validate-collection-umm here when validate-collection-umm is removed

  (concat
    (if-let [err-messages (seq (json-schema/validate-umm-json
                                (umm-json/umm->json collection)
                                :collection))]
      (if (or (:validate-umm? validation-options) (config/return-umm-json-validation-errors))
        (errors/throw-service-errors :invalid-data err-messages)
        (do
         (warn "UMM-C JSON-Schema Validation Errors: " (pr-str (vec err-messages)))
         (str (vec err-messages)))))

    (when-let [err-messages (seq (umm-spec-validation/validate-collection
                                  collection
                                  (when (:validate-keywords? validation-options)
                                    [(keyword-validations context)])))]
      (if (or (:validate-umm? validation-options) (config/return-umm-spec-validation-errors))
        (errors/throw-service-errors :invalid-data err-messages)
        (warn "UMM-C UMM Spec Validation Errors: " (pr-str (vec err-messages)))))))

(defn validate-granule-umm-spec
  "Validates a UMM granule record using rules defined in UMM Spec with a UMM Spec collection record"
  [context collection granule]
  (when-let [errors (seq (umm-spec-validation/validate-granule collection granule))]
    (if (config/return-umm-spec-validation-errors)
      (if-errors-throw :invalid-data errors)
      (warn (format "Granule with Granule UR [%s] had the following UMM Spec validation errors: %s"
                    (:granule-ur granule) (pr-str (vec errors)))))))

(defn validate-granule-umm
  [context collection granule]
  (if-errors-throw :invalid-data (umm-validation/validate-granule collection granule)))

(defn validate-business-rules
  "Validates the concept against CMR ingest rules."
  [context concept]
  (if-errors-throw :invalid-data (mapcat #(% context concept)
                                         (bv/business-rule-validations (:concept-type concept)))))
