(ns cmr.common.mime-types
  "Provides vars and functions for parsing and generating between MIME
  type and HTTP Content-Type strings and data formats supported by the
  CMR."
  (:refer-clojure :exclude [atom])
  (:require [clojure.string :as str]
            [cmr.common.util :as util]
            [cmr.common.services.errors :as svc-errors]
            [ring.middleware.format-response :as fr]))

;; (note: these maps will be populated as MIME types are declared)

(def base-mime-type-to-format
  "A map of MIME type strings to CMR data format keywords."
  {})

(def format->mime-type
  "A map of CMR data format keywords to MIME type strings."
  {})

;; Supported MIME types

(defmacro defmimetype
  "An elegant DSL for crafting beautiful mime-type definitions."
  [name string format-kw & format-aliases]
  (alter-var-root #'base-mime-type-to-format assoc string format-kw)
  (doseq [kw (conj format-aliases format-kw)]
    (alter-var-root #'format->mime-type assoc kw string))
  `(def ~name ~string))

(defmimetype json "application/json" :json)

(defmimetype xml "application/xml" :xml)

(defmimetype echo10 "application/echo10+xml" :echo10)

(defmimetype iso-smap "application/iso:smap+xml" :iso-smap :iso_smap)

(defmimetype iso "application/iso19115+xml" :iso19115 :iso)

(defmimetype dif "application/dif+xml" :dif)

(defmimetype dif10 "application/dif10+xml" :dif10)

(defmimetype csv "text/csv" :csv)

(defmimetype atom  "application/atom+xml" :atom)

(defmimetype kml "application/vnd.google-earth.kml+xml" :kml)

(defmimetype opendata "application/opendata+json" :opendata)

(defmimetype native "application/metadata+xml" :native)

(def any "*/*")

;; extra helpers

(def all-supported-mime-types
  "A superset of all mime types supported by any CMR applications."
  (keys base-mime-type-to-format))

(defn mime-type->format
  "Converts a mime-type into the format requested."
  ([mime-type]
   (mime-type->format mime-type json))
  ([mime-type default-mime-type]
   (if mime-type
     (or (get base-mime-type-to-format mime-type)
         (get base-mime-type-to-format default-mime-type))
     (get base-mime-type-to-format default-mime-type))))

;; Content-Type utilities

(defn with-charset
  "Returns a Content-Type header string with the given mime-type and charset."
  [mime-type charset]
  (str mime-type "; charset=" charset))

(defn with-utf-8
  "Returns mimetype with utf-8 charset specified."
  [mime-type]
  (with-charset mime-type "utf-8"))

(defn extract-mime-types
  "Extracts mime types from an accept header string according to RFC 2616 and returns them
  as combined type/sub-type strings in order of preference.
  Example from spec:

  audio/*; q=0.2, audio/basic

  \"SHOULD be interpreted as \"I prefer audio/basic, but send me any audio
   type if it is the best available after an 80% mark-down in quality.\"\"

  This function will return [\"audio/basic\" \"audio/*\"]

  Note that we do not currently handle asterisks and matching them. So \"*/xml\" would not match
  application/xml."
  [mime-type-str]
  (when mime-type-str
    (for [{:keys [sub-type type]} (fr/parse-accept-header* mime-type-str)]
      (str type "/" sub-type))))

(defn mime-type-from-headers
  "Returns first acceptable preferred mime-type based on the 'accept' or 'content-type' headers or the
  first preferred mime type if none of them were acceptable"
  ([headers]
   (mime-type-from-headers headers all-supported-mime-types))
  ([headers potential-mime-types]
   (let [headers (util/map-keys str/lower-case headers)]
     (some (set potential-mime-types)
           (concat (extract-mime-types (get headers "accept"))
                   (extract-mime-types (get headers "content-type")))))))

(defn path-w-extension->mime-type
  "Parses the search path with extension and returns the requested mime-type or nil if no extension
  was passed."
  [search-path-w-extension]
  (when-let [extension (second (re-matches #"[^.]+(?:\.(.+))$" search-path-w-extension))]
    (format->mime-type (keyword extension))))

(defn extract-header-mime-type
  "Extracts the given header value from the headers and returns the first valid preferred mime type.
  If validate? is true it will throw an error if the header was passed by the client but no mime type
  in the header value was acceptable."
  [valid-mime-types headers header validate?]
  (when-let [header-value (get headers header)]
    (or (some valid-mime-types (extract-mime-types header-value))
        (when validate?
          (svc-errors/throw-service-error
            :bad-request (format "The mime types specified in the %s header [%s] are not supported."
                                 header header-value))))))

(defn get-results-format
  "Returns the requested results format parsed from the URL extension.  If the URL extension does
  not designate the format, then determine the mime-type from the accept and content-type headers.
  If the format still cannot be determined return the default-mime-type as passed in."
  ([path-w-extension headers default-mime-type]
   (get-results-format
     path-w-extension headers all-supported-mime-types default-mime-type))
  ([path-w-extension headers valid-mime-types default-mime-type]
   (or (path-w-extension->mime-type path-w-extension)
       (mime-type-from-headers headers valid-mime-types)
       default-mime-type)))
