(ns cmr.umm-spec.xml-to-umm-mappings.echo10.related-url
  (:require [cmr.umm-spec.xml.parse :refer :all]
            [cmr.umm-spec.simple-xpath :refer [select text]]
            [clojure.string :as str]))

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
   "DATA ACCESS (OPENDAP)" ["GET DATA" "OPENDAP DATA (DODS)"]
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
   "OPENDAP" ["GET DATA" "OPENDAP DATA (DODS)"]
   "DATA ACCESS" ["GET DATA"]
   "ALGORITHM INFO" ["VIEW RELATED INFORMATION"]})

(defn- parse-online-resource-urls
  "Parse online resource urls"
  [doc]
  (for [resource (select doc "/Collection/OnlineResources/OnlineResource")
        :let [resource-type (value-of resource "Type")
              [type sub-type] (resource-type->related-url-types
                                (when resource-type (str/upper-case resource-type)))
              description (value-of resource "Description")]]
    {:URLs [(value-of resource "URL")]
     :Description description
     :ContentType {:Type type
                   :Subtype sub-type}
     :MimeType (value-of resource "MimeType")}))

(defn- parse-online-access-urls
  "Parse online access urls"
  [doc]
  (for [resource (select doc "/Collection/OnlineAccessURLs/OnlineAccessURL")]
    {:URLs [(value-of resource "URL")]
     :Description (value-of resource "URLDescription")
     :MimeType (value-of resource "MimeType")
     :ContentType {:Type "GET DATA"}}))

(defn- parse-browse-urls
  "Parse browse urls"
  [doc]
  (for [resource (select doc "/Collection/AssociatedBrowseImageUrls/ProviderBrowseUrl")
        :let [file-size (value-of resource "FileSize")]]
    {:URLs [(value-of resource "URL")]
     :FileSize (when file-size {:Size file-size :Unit "Bytes"})
     :Description (value-of resource "Description")
     :MimeType (value-of resource "MimeType")
     :ContentType {:Type "GET RELATED VISUALIZATION"}}))

(defn parse-related-urls
  "Returns related-urls elements from a parsed XML structure"
  [doc]
  (seq (concat (parse-online-access-urls doc)
               (parse-online-resource-urls doc)
               (parse-browse-urls doc))))