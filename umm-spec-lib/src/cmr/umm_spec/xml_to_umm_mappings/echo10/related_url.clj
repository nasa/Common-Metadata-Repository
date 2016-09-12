(ns cmr.umm-spec.xml-to-umm-mappings.echo10.related-url
  (:require
   [clojure.string :as str]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select text]]
   [cmr.umm-spec.util :as su]))

(def resource-type->related-url-types
  "A mapping of ECHO10 OnlineResource's type to UMM RelatedURL's type and sub-type.
  This came from a list provided by Katie on ECHO10 collections, more may need to be added for granules."
  {"STATIC URL" ["VIEW RELATED INFORMATION"]
   "GUIDE" ["VIEW RELATED INFORMATION" "USER'S GUIDE"]
   "HOMEPAGE" ["VIEW PROJECT HOME PAGE"]
   "SOFTWARE" ["GET SERVICE" "GET SOFTWARE PACKAGE"]
   "CURRENT BROWSE BY CHANNEL" ["GET RELATED VISUALIZATION"]
   "BROWSE" ["GET RELATED VISUALIZATION"]
   "CITING GHRC DATA" ["GET DATA"]
   "PI DOCUMENTATION" ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
   "DATASETDISCLAIMER" ["VIEW RELATED INFORMATION"]
   "ECSCOLLGUIDE" ["VIEW RELATED INFORMATION" "USER'S GUIDE"]
   "MISCINFORMATION" ["VIEW RELATED INFORMATION"]
   "TEMPERATURE TREND GRAPHS" ["GET RELATED VISUALIZATION"]
   "DATA SET GUIDE" ["VIEW RELATED INFORMATION" "USER'S GUIDE"]
   "REFERENCE MATERIALS" ["VIEW RELATED INFORMATION"]
   "THUMBNAIL" ["GET RELATED VISUALIZATION"]
   "DATASET QUALITY SUMMARY" ["VIEW RELATED INFORMATION"]
   "ONLINERESOURCETYPE" ["VIEW RELATED INFORMATION"]
   "PROJECT HOME PAGE" ["VIEW PROJECT HOME PAGE"]
   "USER SUPPORT" ["VIEW RELATED INFORMATION"]
   "DATA ACCESS (FTP)" ["GET DATA"]
   "CALIBRATION/VALIDATION DATA" ["VIEW RELATED INFORMATION"]
   "PORTAL_DA_DIRECT_ACCESS" ["GET DATA"]
   "ICE HOME PAGE" ["VIEW PROJECT HOME PAGE"]
   "PROJECT HOMEPAGE" ["VIEW PROJECT HOME PAGE"]
   "PRODUCT QUALITY MONITORING PAGE" ["VIEW RELATED INFORMATION"]
   "GHRSST PORTAL HOME PAGE" ["VIEW PROJECT HOME PAGE"]
   "DOI" ["VIEW PROJECT HOME PAGE"]
   "RELATED URLS" ["VIEW RELATED INFORMATION"]
   "DATA DOCUMENTATION" ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
   "IMPORTANT NOTICE" ["VIEW RELATED INFORMATION"]
   "PROJECT WEBSITE" ["VIEW PROJECT HOME PAGE"]
   "WEB SITE" ["VIEW PROJECT HOME PAGE"]
   "TEXT/HTML" ["VIEW RELATED INFORMATION"]
   "PRODUCT INFO" ["VIEW RELATED INFORMATION"]
   "OVERVIEW" ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
   "BROWSE CALENDAR" ["VIEW RELATED INFORMATION"]
   "ALGORITHM INFORMATION" ["VIEW RELATED INFORMATION"]
   "DATA ACCESS" ["GET DATA"]
   "ALGORITHM INFO" ["VIEW RELATED INFORMATION"]
   "GET DATA : OPENDAP DATA (DODS)" ["OPENDAP DATA ACCESS"]})

(defn- parse-online-resource-urls
  "Parse online resource urls"
  [doc]
  (for [resource (select doc "/Collection/OnlineResources/OnlineResource")
        :let [resource-type (value-of resource "Type")
              [type sub-type] (resource-type->related-url-types
                                (when resource-type (str/upper-case resource-type)))
              ;; Check for opendap (case-insensitive) in OnlineResource Type when no defined type is found.
              ;; This is due to GES_DISC OnlineResource opendap could use any string that contains opendap.
              ;; See CMR-2555 for details
              type (or type
                       (when (and resource-type
                                  (re-find #"^.*OPENDAP.*$" (str/upper-case resource-type)))
                         "OPENDAP DATA ACCESS"))
              description (value-of resource "Description")]]
    {:URLs [(value-of resource "URL")]
     :Description description
     :Relation (when type [type sub-type])
     :MimeType (value-of resource "MimeType")}))

(defn- parse-online-access-urls
  "Parse online access urls"
  [doc]
  (for [resource (select doc "/Collection/OnlineAccessURLs/OnlineAccessURL")]
    {:URLs [(value-of resource "URL")]
     :Description (value-of resource "URLDescription")
     :MimeType (value-of resource "MimeType")
     :Relation ["GET DATA"]}))

(defn- parse-browse-urls
  "Parse browse urls"
  [doc]
  (for [resource (select doc "/Collection/AssociatedBrowseImageUrls/ProviderBrowseUrl")
        :let [file-size (value-of resource "FileSize")]]
    {:URLs [(value-of resource "URL")]
     :FileSize (when file-size {:Size (/ (Long. file-size) 1024) :Unit "KB"})
     :Description (value-of resource "Description")
     :MimeType (value-of resource "MimeType")
     :Relation ["GET RELATED VISUALIZATION"]}))

(defn cleanup-urls
  "Fixes bad URLs with whitespace in the middle."
  [related-url]
  (update related-url :URLs
          (fn [urls]
            (seq
             (map (fn [s]
                    (str/replace s #"(?s)\s+" ""))
                  urls)))))

(defn parse-related-urls
  "Returns related-urls elements from a parsed XML structure"
  [doc apply-default?]
  (if-let [related-urls (concat (parse-online-access-urls doc)
                                (parse-online-resource-urls doc)
                                (parse-browse-urls doc))]
    (seq (map cleanup-urls related-urls))
    (when apply-default?
      [su/not-provided-related-url])))
