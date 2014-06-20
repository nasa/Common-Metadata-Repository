(ns cmr.common.mime-types
  "Provides functions for handling mime types."
  (:require [pantomime.media :as mt]
            [cmr.common.services.errors :as errors]))

(def default-result-format :json)

(def base-mime-type-to-format
  "A map of base mime types to the format symbols supported"
  {"*/*" default-result-format
   "application/json" :json
   "application/xml" :xml
   "application/echo10+xml" :echo10
   "application/iso_prototype+xml" :iso-prototype
   "application/iso:smap+xml" :iso-smap
   "application/iso19115+xml" :iso19115
   "application/dif+xml" :dif})

(def format->mime-type
  {:json "application/json"
   :xml "application/xml"
   :echo10 "application/echo10+xml"
   :iso-prototype "application/iso_prototype+xml"
   :iso-smap "application/iso:smap+xml"
   :iso19115 "application/iso19115+xml"
   :dif "application/dif+xml"})

(defn mime-type->format
  "Converts a mime-type into the format requested."
  [mime-type]
  (if mime-type
    (get base-mime-type-to-format
         (str (mt/base-type (mt/parse mime-type))))
    default-result-format))

(defn validate-request-mime-type
  "Validates the requested mime type is supported."
  [mime-type supported-types]
  (when-not (get supported-types mime-type)
    (errors/throw-service-error
      :bad-request (format "The mime type [%s] is not supported." mime-type))))

(defn get-request-format
  "Returns the requested format parsed from headers"
  [headers supported-types]
  (let [mime-type (get headers "accept")]
    (validate-request-mime-type mime-type supported-types)
    (mime-type->format mime-type)))