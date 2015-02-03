(ns cmr.common.mime-types
  "Provides functions for handling mime types."
  (:require [clojure.string :as str]))

(def all-supported-mime-types
  "A superset of all mime types supported by any CMR applications."
  #{"application/xml"
    "application/json"
    "application/echo10+xml"
    "application/dif+xml"
    "application/atom+xml"
    "application/iso19115+xml"
    "application/iso:smap+xml"
    "application/opendata+json"
    "text/csv"
    "application/vnd.google-earth.kml+xml"})

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
   "application/vnd.google-earth.kml+xml" :kml
   "application/opendata+json" :opendata})

(def format->mime-type
  "A map of format symbols to their mime type."
  {:json "application/json"
   :xml "application/xml"
   :echo10 "application/echo10+xml"
   :iso_smap "application/iso:smap+xml"
   :iso-smap "application/iso:smap+xml"
   :iso "application/iso19115+xml"
   :iso19115 "application/iso19115+xml"
   :dif "application/dif+xml"
   :csv "text/csv"
   :atom "application/atom+xml"
   :kml "application/vnd.google-earth.kml+xml"
   :opendata "application/opendata+json"})

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
  "Returns a mime-type based on the 'accept' or 'content-type' headers.  If an accept header is
  passed in it will attempt to match it against a known potential mime-type.  If there is an
  accept header, but it cannot be matched, it will still be returned and is up to the caller
  to determine appropriate action.  If no accept header is included then it will return the
  content-type header.  Note that */* is treated as if no accept header was passed in."
  [headers potential-mime-types]
  (let [orig-mime-type-str (get headers "accept")
        ;; Strip out any semicolon clauses
        mime-type-str (when orig-mime-type-str
                        (str/replace orig-mime-type-str  #";.*?(,|$)" "$1"))
        ;; Split mime-type string on commas
        mime-types (when mime-type-str (str/split (str/lower-case mime-type-str), #"[,]"))
        first-potential-mime-type (some (set mime-types) potential-mime-types)
        accept-mime-type (if (= "*/*" orig-mime-type-str)
                           nil
                           (or first-potential-mime-type orig-mime-type-str))
        content-type-mime-type (when-not accept-mime-type (get headers "content-type"))]
    (or accept-mime-type content-type-mime-type)))

(defn path-w-extension->mime-type
  "Parses the search path with extension and returns the requested mime-type or nil if no extension
  was passed."
  [search-path-w-extension]
  (when-let [extension (second (re-matches #"[^.]+(?:\.(.+))$" search-path-w-extension))]
    (format->mime-type (keyword extension))))

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
