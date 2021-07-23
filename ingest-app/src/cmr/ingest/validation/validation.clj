(ns cmr.ingest.validation.validation
  "Provides functions to validate concept"
  (:require
    [cheshire.core :as json]
    [clojure.data :as data]
    [clojure.string :as str]
    [cmr.common.util :as util :refer [defn-timed]]
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
    [cmr.transmit.config :as transmit-config]
    [cmr.transmit.search :as transmit-search]
    [cmr.umm-spec.json-schema :as json-schema]
    [cmr.umm-spec.umm-json :as umm-json]
    [cmr.umm-spec.umm-spec-core :as umm-spec]
    [cmr.umm-spec.validation.umm-spec-validation-core :as umm-spec-validation]
    [cmr.umm-spec.versioning :as umm-versioning]
    [cmr.umm-spec.validation.umm-spec-validation-core :as umm-spec-validation]))

(def ^:private
  valid-concept-mime-types
  {:collection #{mt/echo10 mt/iso-smap mt/iso19115 mt/dif mt/dif10 mt/umm-json}
   :granule #{mt/echo10 mt/iso-smap mt/umm-json}
   :variable #{mt/umm-json}
   :subscription #{mt/umm-json}
   :service #{mt/umm-json}
   :tool #{mt/umm-json}})


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
    (errors/throw-service-error :bad-request "Request content is too short.")))

(defn-timed validate-concept-request
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

(defn match-getdata-format-kms-keywords-validation
  "A validation that checks that the format matches granule-data-format  KMS field.
  Takes the following arguments:

  * kms-index - The keywords map as returned by kms-fetcher/get-kms-index
  * msg-fn - A function taking the value and returning the error to return to the user if it doesn't
  match."
  [kms-index msg-fn]
  (fn [field-path value]
    (when value
      (when-not (kms-lookup/lookup-by-umm-c-keyword kms-index :granule-data-format value)
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
                kms-index :projects msg/project-not-matches-kms-keywords)
     :LocationKeywords (match-kms-keywords-validation
                        kms-index :spatial-keywords msg/location-keyword-not-matches-kms-keywords)
     :DataCenters (match-kms-keywords-validation
                   kms-index :providers msg/data-center-not-matches-kms-keywords)
     :DirectoryNames (match-kms-keywords-validation
                      kms-index :concepts msg/directory-name-not-matches-kms-keywords)
     :ISOTopicCategories (match-kms-keywords-validation
                          kms-index :iso-topic-categories msg/iso-topic-category-not-matches-kms-keywords)
     :ArchiveAndDistributionInformation
      {:FileDistributionInformation
       (match-kms-keywords-validation
        kms-index :granule-data-format msg/data-format-not-matches-kms-keywords)
       :FileArchiveInformation
       (match-kms-keywords-validation
        kms-index :granule-data-format msg/data-format-not-matches-kms-keywords)}
     :RelatedUrls
      [(match-kms-keywords-validation
          kms-index
          :related-urls
          msg/related-url-type-subtype-not-matching-kms-keywords)
        (v/every {:GetData {:Format (match-getdata-format-kms-keywords-validation
                                     kms-index
                                     msg/getdata-format-not-matches-kms-keywords)}})]}))

(defn- pad-zeros-to-version
  "Pad 0's to umm versions. Example: 1.9.1 becomes 01.09.01, 1.10.1 becomes 01.10.01"
  [version]
  (let [version-splitted (str/split version #"\.")]
    (str/join "." (map #(if (> 10 (Integer. %)) (str "0" %) %) version-splitted))))

(defn- compare-versions-with-padded-zeros
  "Compare the umm-version and accepted umm-version
   with padded 0's."
  [umm-version accepted-umm-version]
  (let [umm-version-with-padded-zeros (pad-zeros-to-version umm-version)
        accepted-umm-version-with-padded-zeros (pad-zeros-to-version accepted-umm-version)]
    (compare umm-version-with-padded-zeros accepted-umm-version-with-padded-zeros)))

(defn umm-version-valid?
  "Check if umm-version is valid for concept-type."
  [umm-version concept-type]
  (let [valid-umm-versions (concept-type umm-versioning/versions)]
    (some #(= umm-version %) valid-umm-versions)))

(defn-timed validate-concept-metadata
  [concept]
  (if-errors-throw :bad-request
                   (if (mt/umm-json? (:format concept))
                     (let [umm-version (mt/version-of (:format concept))
                           concept-type (:concept-type concept)
                           accept-version (config/ingest-accept-umm-version concept-type)]
                       ;; when the umm-version goes to 1.10 and accept-version is 1.9, we need
                       ;; to compare the versions with padded zeros.
                       (if (umm-version-valid? umm-version concept-type)
                         (if (>= 0 (compare-versions-with-padded-zeros umm-version accept-version))
                           (umm-spec/validate-metadata (:concept-type concept)
                                                       (:format concept)
                                                       (:metadata concept))
                           [(str "UMM JSON version " accept-version  " or lower can be ingested. "
                                 "Any version above that is considered in-development "
                                 "and cannot be ingested at this time.")])
                        [(str "Invalid UMM JSON schema version: " umm-version )]))
                     (umm-spec/validate-metadata (:concept-type concept)
                                                 (:format concept)
                                                 (:metadata concept)))))

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
  ([collection validation-options context warn?]
   (umm-spec-validate-collection collection nil validation-options context warn?))
  ([collection prev-collection validation-options context warn?]
   (when-let [err-messages (seq (umm-spec-validation/validate-collection
                                 collection
                                 (when (:validate-keywords? validation-options)
                                   [(keyword-validations context)])))]
     (if (or (:validate-umm? validation-options)
             (config/return-umm-spec-validation-errors)
             (not warn?))
       ;; whenever it's time to throw errors, we want to check if it's an collection update and
       ;; it's not bulk-update and progressive-update-enabled is true. If so, we want to throw
       ;; errors only when new errors are introduced, otherwise return all the existing errors as
       ;; error-warnings.
       (if (and (config/progressive-update-enabled)
                (not (:bulk-update? validation-options)) 
                prev-collection)
         (let [prev-err-messages (if (and (:test-existing-errors? validation-options)
                                          ;; double check to make sure only the local and ci tests can use the header.
                                          (transmit-config/echo-system-token? context)
                                          (= "mock-echo-system-token" (:token context)))
                                   ;; We can't really test the case when the errors are existing errors
                                   ;; because we can't ingest invalid collections into the system.
                                   ;; We can only mimic the case when the validation errors for the updated
                                   ;; collection are the same as the validation errors for the previous revision
                                   ;; of the collection.
                                   err-messages
                                   (seq (umm-spec-validation/validate-collection
                                       prev-collection
                                       (when (:validate-keywords? validation-options)
                                         [(keyword-validations context)]))))
               ;; get the newly introduced validation errors
               new-err-messages (seq (first (data/diff (set err-messages) (set prev-err-messages))))]
            (if new-err-messages
              (errors/throw-service-errors :invalid-data new-err-messages)
              ;; when there is no newly introduced errors, err-messages contains only existing errors.
              [{:existing-errors (map #(str (:path %) " " (:errors %)) err-messages)}]))
         (errors/throw-service-errors :invalid-data err-messages))
       (do
         (warn "UMM-C UMM Spec Validation Errors: " (pr-str (vec err-messages)))
         err-messages)))))

(defn umm-spec-validate-collection-warnings
  "Validate umm-spec collection validation warnings functions - errors that we want
  to report but we do not want to fail ingest."
  [collection validation-options context]
  (when-let [err-messages (seq (umm-spec-validation/validate-collection-warnings
                                collection))]
    (if (or (:validate-umm? validation-options)
            (config/return-umm-spec-validation-errors))
      (errors/throw-service-errors :invalid-data err-messages)
      (do
        (warn "UMM-C UMM Spec Validation Errors: " (pr-str (vec err-messages)))
        err-messages))))

(defn-timed validate-granule-umm-spec
  "Validates a UMM granule record using rules defined in UMM Spec with a UMM Spec collection record,
  updated with platform aliases whoes shortnames don't exist in the platforms."
  [context collection granule]
  (when-let [errors (seq (umm-spec-validation/validate-granule
                          (humanizer-alias-cache/update-collection-with-aliases context
                                                                                collection
                                                                                true)
                          granule))]
    (if-errors-throw :invalid-data errors)))

(defn validate-granule-umm
  "Validates a UMM granule record using rules defined in UMM with a UMM collection record,
  updated with platform aliases whoes shortnames don't exist in the platforms."
  [context collection granule]
  (if-errors-throw :invalid-data (umm-spec-validation/validate-granule
                                  (humanizer-alias-cache/update-collection-with-aliases
                                   context collection false)
                                  granule)))

(defn-timed validate-business-rules
  "Validates the concept against CMR ingest rules."
  ([context concept]
   (validate-business-rules context concept nil))
  ([context concept prev-concept]
   (if-errors-throw :invalid-data
                    (mapcat #(% context concept prev-concept)
                            (bv/business-rule-validations
                             (:concept-type concept))))))

(defn- measurement-validation
  "A validation that checks that the measurement matches a known KMS field. Takes the following arguments:

  * kms-index - The keywords map as returned by kms-fetcher/get-kms-index
  * msg-fn - A function taking the invalid measurements and returning the error to return to the user
    if it doesn't match."
  [kms-index msg-fn]
  (v/every
    (fn [field-path value]
      (when-let [invalid-measurements (kms-lookup/lookup-by-measurement kms-index value)]
        {field-path [(msg-fn invalid-measurements)]}))))

(defn- variable-keyword-validations
  "Creates validations that check various collection fields to see if they match KMS keywords."
  [context]
  (let [kms-index (kms-fetcher/get-kms-index context)]
    {:MeasurementIdentifiers (measurement-validation
                              kms-index msg/measurements-not-matches-kms-keywords)}))

(defn umm-spec-validate-variable
  "Validate variable through umm-spec validation functions. If warn? flag is
  true and umm-spec-validation is off, log warnings and return messages, otherwise throw errors."
  [variable context warn?]
  (when-let [err-messages (seq (umm-spec-validation/validate-variable
                                variable
                                [(variable-keyword-validations context)]))]
    (if (or (config/return-umm-spec-validation-errors)
            (not warn?))
      (errors/throw-service-errors :invalid-data err-messages)
      (do
        (warn "UMM-Var UMM Spec Validation Errors: " (pr-str (vec err-messages)))
        err-messages))))

(defn validate-variable-associated-collection
  "Validate the collection being associated to is accessible."
  [context coll-concept-id coll-revision-id]
  (when coll-concept-id
    (let [params (if coll-revision-id
                   {:concept-id coll-concept-id
                    :all-revisions true}
                   {:concept-id coll-concept-id})
          response (transmit-search/search-for-collections
                     context
                     params
                     {:raw? true
                      :http-options {:accept mt/umm-json}})
          items (-> response
                    :body
                    (json/parse-string true)
                    :items)
          revision-info (->> items
                             (map :meta)
                             (map #(select-keys % [:revision-id :deleted])))]
      (if (seq revision-info)
        (when coll-revision-id
          (if-let [revision (->> revision-info
                                 (filter #(= (read-string coll-revision-id)
                                             (:revision-id %)))
                                 first)]
            (when (:deleted revision)
              (errors/throw-service-error
                :invalid-data
                (format "Collection [%s] revision [%s] is deleted"
                        coll-concept-id coll-revision-id)))
            (errors/throw-service-error
              :invalid-data
              (format "Collection [%s] revision [%s] does not exist"
                      coll-concept-id coll-revision-id))))
        (errors/throw-service-error
          :invalid-data
          (format "Collection [%s] does not exist or is not visible."
                  coll-concept-id))))))
