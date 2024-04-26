(ns cmr.umm-spec.umm-g.related-url
  "Contains functions for parsing UMM-G JSON related-urls into umm-lib granule model
  RelatedURLs and generating UMM-G JSON related-urls from umm-lib granule model RelatedURLs."
  (:require
   [cmr.umm.umm-collection :as umm-c])
  (:import
   (cmr.umm.umm_collection RelatedURL)))

(defn- umm-g-related-url->RelatedURL
  "Returns the umm-lib granule model RelatedURL from the given UMM-G RelatedUrl."
  [related-url]
  (let [{:keys [URL Type Subtype Description MimeType Size Format]} related-url]
    (umm-c/map->RelatedURL
     {:type Type
      :sub-type Subtype
      :url URL
      :description Description
      :mime-type MimeType
      :format Format
      :title Description
      :size Size})))

(defn umm-g-related-urls->RelatedURLs
  "Returns the umm-lib granule model RelatedURLs from the given UMM-G RelatedUrls.
  NOTE: this is converts from the new style of urls to the old style."
  [related-urls]
  (seq (map umm-g-related-url->RelatedURL related-urls)))

(def umm-g-related-url-types
  "Defines the valid UMM-G related url types. This list is based on RelatedUrlTypeEnum in
  umm-g-json-schema.json, and needs to be updated when the UMM-G schema is updated."
  #{"DOWNLOAD SOFTWARE" "EXTENDED METADATA" "GET DATA" "GET DATA VIA DIRECT ACCESS"
    "GET RELATED VISUALIZATION" "GOTO WEB TOOL" "PROJECT HOME PAGE" "USE SERVICE API"
    "VIEW RELATED INFORMATION"})

(def umm-g-related-url-sub-types
  "Defines the valid UMM-G related url sub-types. This list is based on RelatedUrlSubTypeEnum in
  umm-g-json-schema.json, and needs to be updated when the UMM-G schema is updated."
  #{"MOBILE APP" "APPEARS" "DATA COLLECTION BUNDLE" "DATA TREE" "DATACAST URL" "DIRECT DOWNLOAD"
    "EOSDIS DATA POOL" "Earthdata Search" "GIOVANNI" "LAADS" "LANCE" "MIRADOR" "MODAPS" "NOAA CLASS"
    "NOMADS" "PORTAL" "USGS EARTH EXPLORER" "VERTEX" "VIRTUAL COLLECTION" "MAP" "WORLDVIEW"
    "LIVE ACCESS SERVER (LAS)" "MAP VIEWER" "SIMPLE SUBSET WIZARD (SSW)" "SUBSETTER"
    "GRADS DATA SERVER (GDS)" "MAP SERVICE" "OPENDAP DATA" "OpenSearch" "SERVICE CHAINING"
    "TABULAR DATA STREAM (TDS)" "THREDDS DATA" "WEB COVERAGE SERVICE (WCS)"
    "WEB FEATURE SERVICE (WFS)" "WEB MAP SERVICE (WMS)" "WEB MAP TILE SERVICE (WMTS)"
    "ALGORITHM DOCUMENTATION" "ALGORITHM THEORETICAL BASIS DOCUMENT" "ANOMALIES" "CASE STUDY"
    "DATA CITATION POLICY" "DATA QUALITY" "DATA RECIPE" "DELIVERABLES CHECKLIST"
    "GENERAL DOCUMENTATION" "HOW-TO" "IMPORTANT NOTICE""INSTRUMENT/SENSOR CALIBRATION DOCUMENTATION"
    "MICRO ARTICLE" "PI DOCUMENTATION" "PROCESSING HISTORY" "PRODUCT HISTORY"
    "PRODUCT QUALITY ASSESSMENT" "PRODUCT USAGE" "PRODUCTION HISTORY" "PUBLICATIONS" "READ-ME"
    "REQUIREMENTS AND DESIGN" "SCIENCE DATA PRODUCT SOFTWARE DOCUMENTATION"
    "SCIENCE DATA PRODUCT VALIDATION" "USER FEEDBACK" "USER'S GUIDE" "DMR++" "DMR++ MISSING DATA"})

(defn RelatedURLs->umm-g-related-urls
  "Returns the UMM-G RelatedUrls from the given umm-lib granule model RelatedURLs. note, this is old->new"
  [related-urls]
  (when (seq related-urls)
    (for [related-url related-urls]
      (let [{:keys [type sub-type url description mime-type size]} related-url
            umm-g-related-url-type (if-let [umm-g-type (umm-g-related-url-types type)]
                                     umm-g-type
                                     ;; default UMM-G related url type when there is no match
                                     "VIEW RELATED INFORMATION")]
        {:URL url
         :Type umm-g-related-url-type
         :Subtype (umm-g-related-url-sub-types sub-type)
         :Description description
         :MimeType mime-type
         :Size size
         :SizeUnit (when size "NA")}))))
