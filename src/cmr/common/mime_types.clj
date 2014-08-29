(ns cmr.common.mime-types
  "Provides functions for handling mime types."
  (:require [pantomime.media :as mt]
            [cmr.common.services.errors :as errors]))

(def base-mime-type-to-format
  "A map of base mime types to the format symbols supported"
  {"application/json" :json
   "application/xml" :xml
   "application/echo10+xml" :echo10
   "application/iso+xml" :iso
   "application/iso-smap+xml" :iso-smap
   "application/iso-mends+xml" :iso-mends
   "application/iso19115+xml" :iso19115
   "application/dif+xml" :dif
   "text/csv" :csv
   "application/atom+xml" :atom})

(def format->mime-type
  {:json "application/json"
   :xml "application/xml"
   :echo10 "application/echo10+xml"
   :iso "application/iso+xml"
   :iso-smap "application/iso-smap+xml"
   :iso-mends "application/iso-mends+xml"
   :iso19115 "application/iso19115+xml"
   :dif "application/dif+xml"
   :csv "text/csv"
   :atom "application/atom+xml"})

(defn- base-mime-type
  [mime-type]
  (str (mt/base-type (mt/parse mime-type))))

(defn mime-type->format
  "Converts a mime-type into the format requested."
  ([mime-type]
   (mime-type->format mime-type "application/json"))
  ([mime-type default-mime-type]
   (if mime-type
     (or (get base-mime-type-to-format (base-mime-type mime-type))
         (get base-mime-type-to-format (base-mime-type default-mime-type)))
     (get base-mime-type-to-format (base-mime-type default-mime-type)))))

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