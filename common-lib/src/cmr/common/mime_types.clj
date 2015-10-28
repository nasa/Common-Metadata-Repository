(ns cmr.common.mime-types
  "Provides vars and functions for parsing and generating between MIME
  type and HTTP Content-Type strings and data formats supported by the
  CMR."
  (:refer-clojure :exclude [atom])
  (:require [clojure.string :as str]
            [cmr.common.util :as util]
            [cmr.common.services.errors :as svc-errors]
            [ring.middleware.format-response :as fr]))

(def mime-types
  "Defines a map of mime type format keywords to mime types and other format aliases. Each one of these
   has a var defined for it for easy access."
  {:json {:mime-type "application/json"}
   :umm-json {:mime-type "application/umm+json"}
   :xml {:mime-type "application/xml"}
   :form-url-encoded {:mime-type "application/x-www-form-urlencoded"}
   :echo10 {:mime-type "application/echo10+xml"}
   :iso-smap {:mime-type "application/iso:smap+xml"
              :aliases [:iso_smap]}
   :iso19115 {:mime-type "application/iso19115+xml"
              :aliases [:iso]}
   :dif {:mime-type "application/dif+xml"}
   :dif10 {:mime-type "application/dif10+xml"}
   :csv {:mime-type "text/csv"}
   :atom {:mime-type  "application/atom+xml"}
   :kml {:mime-type "application/vnd.google-earth.kml+xml"}
   :opendata {:mime-type "application/opendata+json"}
   :native {:mime-type "application/metadata+xml"}
   :edn {:mime-type "application/edn"}
   :opendap {:mime-type "application/x-netcdf"}
   :serf {:mime-type "application/serf+xml"}})

;; Define vars for each of the mime type formats, e.g. (def json "application/json")
(doseq [[format-kw {:keys [mime-type]}] mime-types]
  (eval `(def ~(symbol (name format-kw)) ~mime-type)))

(def iso
  "Defines a shorter alias for iso19115."
  iso19115)

(def any "*/*")

(def base-mime-type-to-format
  "A map of MIME type strings to CMR data format keywords."
  (into {} (for [[format-kw {:keys [mime-type]}] mime-types]
             [mime-type format-kw])))

(def format->mime-type
  "A map of CMR data format keywords to MIME type strings."
  (into {} (mapcat (fn [[format-kw {:keys [mime-type aliases]}]]
                     (cons [format-kw mime-type]
                            (for [a aliases]
                              [a mime-type])))
                   mime-types)))
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

(defn mime-type-from-header
  "Returns first acceptable preferred mime-type from the given header."
  [header-value potential-mime-types]
  (some (set potential-mime-types) (extract-mime-types header-value)))

(defn accept-mime-type
  "Returns the first accepted mime type passed in the Accept header"
  ([headers]
   (accept-mime-type headers all-supported-mime-types))
  ([headers potential-mime-types]
   (mime-type-from-header (get (util/map-keys str/lower-case headers) "accept")
                          potential-mime-types)))

(defn content-type-mime-type
  "Returns the mime type passed in the Content-Type header"
  ([headers]
   (content-type-mime-type headers all-supported-mime-types))
  ([headers potential-mime-types]
   (mime-type-from-header (get (util/map-keys str/lower-case headers) "content-type")
                          potential-mime-types)))

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
       (accept-mime-type headers valid-mime-types)
       (content-type-mime-type headers valid-mime-types)
       default-mime-type)))
