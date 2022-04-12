(ns cmr.search.services.url-helper
  "Defines functions to construct search urls"
  (:require
    [cmr.common.config :as cfg]
    [cmr.transmit.config :as tconfig]))

(defn search-root
  "Returns the url root for reference location"
  [context]
  (tconfig/application-public-root-url context))

(defn reference-root
  "Returns the url root for reference location"
  [context]
  (str (search-root context) "concepts/"))

(defn concept-stac-url
  "Returns the STAC url for a given concept id"
  [context concept-id]
  (format "%s%s.stac" (reference-root context) concept-id))

(defn concept-xml-url
  "Returns the xml url for a given concept id"
  [context concept-id]
  (format "%s%s.xml" (reference-root context) concept-id))

(defn concept-html-url
  "Returns the html url for a given concept id"
  [context concept-id]
  (format "%s%s.html" (reference-root context) concept-id))

(defn concept-json-url
  "Returns the JSON url for a given concept id"
  [context concept-id]
  (format "%s%s.json" (reference-root context) concept-id))

(defn concept-umm-json-url
  "Returns the UMM JSON url for a given concept id"
  [context concept-id]
  (format "%s%s.umm_json" (reference-root context) concept-id))

(defn atom-request-url
  "Returns the atom request url based on search concept type with extension and query string"
  [context concept-type result-format]
  (let [{:keys [query-string]} context
        query-string (if (empty? query-string) "" (str "?" query-string))]
    (format "%s%ss.%s%s" (tconfig/application-public-root-url context)
            (name concept-type) (name result-format) query-string)))

(defn stac-request-url
  "Returns the request url for granule search in STAC format"
  ([context]
   (format "%sgranules.stac"
           (tconfig/application-public-root-url context)))
  ([context query-string]
   (format "%sgranules.stac?%s"
           (tconfig/application-public-root-url context)
           query-string))
  ([context query-string page-num]
   (format "%sgranules.stac?%s&page_num=%s"
           (tconfig/application-public-root-url context)
           query-string
           page-num)))
