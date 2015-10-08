(ns cmr.ingest.services.validation
  "Provides functions to validate concept"
  (:require [clojure.string :as s]
            [cmr.common.services.errors :as errors]
            [cmr.umm.mime-types :as umm-mime-types]
            [cmr.common.mime-types :as mt]
            [cmr.common.log :as log :refer (warn)]
            [cmr.umm.core :as umm]
            [cmr.umm.validation.core :as umm-validation]
            [cmr.ingest.services.business-rule-validation :as bv]
            [cmr.common.validations.core :as v]
            [cmr.common-app.services.kms-fetcher :as kms-fetcher]
            [cmr.ingest.services.messages :as msg]))

(defn- format-validation
  "Validates the format of the concept."
  [concept]
  (let [content-type (:format concept)
        valid-types (umm-mime-types/concept-type->valid-mime-types (:concept-type concept))]
    (if (contains? valid-types content-type)
      []
      [(format "Invalid content-type: %s. Valid content-types: %s."
               content-type (s/join ", " valid-types))])))

(defn- metadata-length-validation
  "Validates the metadata length is not unreasonable."
  [concept]
  (if (> (count (:metadata concept)) 4)
    []
    ["XML content is too short."]))

(def concept-validations
  "A list of the functions that can validate concept."
  [format-validation
   metadata-length-validation])

(defn if-errors-throw
  "Throws an error if there are any errors."
  [errors]
  (when (seq errors)
    (errors/throw-service-errors :bad-request errors)))

(defn validate-concept-request
  "Validates the initial request to ingest a concept. "
  [concept]
  (if-errors-throw (mapcat #(% concept) concept-validations)))

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

(defn validate-concept-xml
  "Validates the concept xml to ingest a concept. "
  [concept]
  (if-errors-throw (umm/validate-concept-xml concept)))

(defn validate-collection-umm
  [context collection validate-keywords?]
  ;; Log any errors from the keyword validation if we are not returning them to the client.
  (when-not validate-keywords?
    (when-let [errors (seq (v/validate (keyword-validations context) collection))]
      (warn (format "Collection with entry title [%s] had the following keyword validation errors: %s"
                    (:entry-title collection) (pr-str errors)))))
  ;; Validate the collection and throw errors that will be sent to the client.
  (if-errors-throw (umm-validation/validate-collection
                     collection
                     (when validate-keywords?
                       [(keyword-validations context)]))))

(defn validate-granule-umm
  [context collection granule]
  (if-errors-throw (umm-validation/validate-granule collection granule)))

(defn validate-business-rules
  "Validates the concept against CMR ingest rules."
  [context concept]
  (if-errors-throw (mapcat #(% context concept)
                           (bv/business-rule-validations (:concept-type concept)))))


