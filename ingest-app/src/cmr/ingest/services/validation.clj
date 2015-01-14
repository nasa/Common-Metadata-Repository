(ns cmr.ingest.services.validation
  "Provides functions to validate concept"
  (:require [cmr.common.services.errors :as err]
            [clojure.string :as s]
            [cmr.umm.mime-types :as umm-mime-types]
            [cmr.umm.core :as umm]))

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

