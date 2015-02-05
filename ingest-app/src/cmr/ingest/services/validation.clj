(ns cmr.ingest.services.validation
  "Provides functions to validate concept"
  (:require [clojure.string :as s]
            [cmr.common.services.errors :as err]
            [cmr.umm.mime-types :as umm-mime-types]
            [cmr.common.mime-types :as mt]
            [cmr.umm.core :as umm]
            [cmr.umm.validation.core :as umm-validation]
            [cmr.ingest.services.business-rule-validation :as bv]))

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
    (err/throw-service-errors :bad-request errors)))

(defn validate-concept-request
  "Validates the initial request to ingest a concept. "
  [concept]
  (if-errors-throw (mapcat #(% concept) concept-validations)))

(defn validate-concept-xml
  "Validates the concept xml to ingest a concept. "
  [concept]
  (if-errors-throw (umm/validate-concept-xml concept)))

(defn validate-umm-record
  [umm]
  (if-errors-throw (umm-validation/validate umm)))

(defn validate-business-rules
  "Validates the concept against CMR ingest rules."
  [context concept]
  (if-errors-throw (mapcat #(% context concept)
                           (bv/business-rule-validations (:concept-type concept)))))


