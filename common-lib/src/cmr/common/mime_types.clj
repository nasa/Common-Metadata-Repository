(ns cmr.common.mime-types
  "Provides functions for handling mime types."
  (:require [pantomime.media :as mt]
            [clojure.string :as str]
            [cmr.common.services.errors :as errors]))

(def base-mime-type-to-format
  "A map of base mime types to the format symbols supported"
  {"application/json" :json
   "application/xml" :xml
   "application/echo10+xml" :echo10
   "application/iso:smap+xml" :iso-smap
   "application/iso19115+xml" :iso19115
   "application/dif+xml" :dif
   "text/csv" :csv
   "application/atom+xml" :atom
   "application/vnd.google-earth.kml+xml" :kml})

(def format->mime-type
  {:json "application/json"
   :xml "application/xml"
   :echo10 "application/echo10+xml"
   :iso-smap "application/iso:smap+xml"
   :iso19115 "application/iso19115+xml"
   :dif "application/dif+xml"
   :csv "text/csv"
   :atom "application/atom+xml"
   :kml "application/vnd.google-earth.kml+xml"})

(defn mime-type->format
  "Converts a mime-type into the format requested."
  ([mime-type]
   (mime-type->format mime-type "application/json"))
  ([mime-type default-mime-type]
   (if mime-type
     (or (get base-mime-type-to-format mime-type)
         (get base-mime-type-to-format default-mime-type))
     (get base-mime-type-to-format default-mime-type))))

(defn mime-type-from-headers
  "Try to get a supported mime-type from the 'accept' header."
  [headers supported-mime-types]
  (let [orig-mime-type-str (get headers "accept")
        ;; Strip out any semicolon clauses
        mime-type-str (when orig-mime-type-str
                        (str/replace orig-mime-type-str  #";.*?(,|$)" "$1"))
        ;; Split mime-type string on commas
        mime-types (when mime-type-str (str/split (str/lower-case mime-type-str), #"[,]"))
        first-supported-mime-type (some (set mime-types) supported-mime-types)]
    (or first-supported-mime-type orig-mime-type-str)))

(defn validate-request-mime-type
  "Validates the requested mime type is supported."
  [mime-type supported-types]
  (when-not (get supported-types mime-type)
    (errors/throw-service-error
      :bad-request (format "The mime type [%s] is not supported." mime-type))))

(defn get-request-format
  "Returns the requested format parsed from headers"
  [headers supported-types]
  (let [mime-type (mime-type-from-headers headers supported-types)]
    (validate-request-mime-type mime-type supported-types)
    (mime-type->format mime-type)))
