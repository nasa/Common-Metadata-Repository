(ns cmr.ingest.services.validation
  "Provides functions to validate concept"
  (:require [cmr.common.services.errors :as err]))

;; body element (metadata) of a request arriving at ingest app should be in xml format and mime type
;; should be of the items in this def.
(def CMR_VALID_CONTENT_TYPES
  #{"application/echo10+xml", "application/iso_prototype+xml", "application/iso:smap+xml",
    "application/iso19115+xml", "application/dif+xml"})

(defn- format-validation
  "Validates the format of the concept."
  [concept]
  (let [content-type (:format concept)]
    (if (contains? CMR_VALID_CONTENT_TYPES content-type)
      []
      [(format "Invalid content-type: %s. Valid content-types: %s."
               content-type CMR_VALID_CONTENT_TYPES)])))

(defn- metadata-length-validation
  "Validates the metadata length is not unreasonable."
  [concept]
  (if (> (count (:metadata concept)) 4)
    []
    ["Invalid XML content."]))

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
