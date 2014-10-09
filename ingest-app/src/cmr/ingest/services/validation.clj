(ns cmr.ingest.services.validation
  "Provides functions to validate concept"
  (:require [cmr.common.services.errors :as err]
            [clojure.string :as s]
            [cmr.umm.mime-types :as umm-mime-types]))

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

(defn validate
  "Validates the given concept. Throws exceptions to send to the user."
  [concept]
  (let [errors (mapcat #(% concept) concept-validations)]
    (when-not (empty? errors)
      (err/throw-service-errors :bad-request errors))))
