(ns cmr.umm.echo10.related-url
  "Contains functions for parsing and generating the ECHO10 OnlineResources and OnlineAccessURLs
  into UMM related urls."
  (:require [clojure.string :as s]
            [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.granule :as g]))

(def resource-type->related-url-types
  "A mapping of ECHO10 OnlineResource's type to UMM RelatedURL's type and sub-type.
  This came from a list provided by Katie on ECHO10 collections, more may need to be added for granules."
  {"STATIC URL" ["GET DATA"]
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

(defn xml-elem->online-resource-url
  [elem]
  (let [url (cx/string-at-path elem [:URL])
        description (cx/string-at-path elem [:Description])
        resource-type (cx/string-at-path elem [:Type])
        [type sub-type] (resource-type->related-url-types (when resource-type (s/upper-case resource-type)))]
    (g/map->RelatedURL
      {:url url
       :description description
       :type type
       :sub-type sub-type})))

(defn- xml-elem->online-resource-urls
  "Returns online-resource-urls elements from a parsed Granule XML structure"
  [granule-content-node]
  (let [urls (map xml-elem->online-resource-url
                  (cx/elements-at-path
                    granule-content-node
                    [:OnlineResources :OnlineResource]))]
    (when-not (empty? urls)
      urls)))

(defn xml-elem->online-access-url
  [elem]
  (let [url (cx/string-at-path elem [:URL])
        description (cx/string-at-path elem [:URLDescription])]
    (g/map->RelatedURL
      {:url url
       :description description
       :type "GET DATA"})))

(defn- xml-elem->online-access-urls
  "Returns online-access-urls elements from a parsed Granule XML structure"
  [granule-content-node]
  (let [urls (map xml-elem->online-access-url
                  (cx/elements-at-path
                    granule-content-node
                    [:OnlineAccessURLs :OnlineAccessURL]))]
    (when-not (empty? urls)
      urls)))

(defn xml-elem->related-urls
  "Returns related-urls elements from a parsed Granule XML structure"
  [granule-content-node]
  (seq (concat (xml-elem->online-access-urls granule-content-node)
               (xml-elem->online-resource-urls granule-content-node))))

(defn downloadable-url?
  "Returns true if the related-url is downloadable"
  [related-url]
  (= "GET DATA" (:type related-url)))

(defn generate-access-urls
  "Generates the OnlineAccessURLs element of an ECHO10 XML from a UMM Granule related urls entry."
  [related-urls]
  (let [downloadable-urls (filter downloadable-url? related-urls)]
    (when-not (empty? downloadable-urls)
      (x/element
        :OnlineAccessURLs {}
        (for [related-url downloadable-urls]
          (let [{:keys [url description]} related-url]
            (x/element :OnlineAccessURL {}
                       (x/element :URL {} url)
                       (x/element :URLDescription {} description))))))))

(defn generate-resource-urls
  "Generates the OnlineResources element of an ECHO10 XML from a UMM Granule related urls entry."
  [related-urls]
  (let [undownloadable-urls (filter (complement downloadable-url?) related-urls)]
    (when-not (empty? undownloadable-urls)
      (x/element
        :OnlineResources {}
        (for [related-url undownloadable-urls]
          (let [{:keys [url description type]} related-url]
            (x/element :OnlineResource {}
                       (x/element :URL {} url)
                       (x/element :Type {} type)
                       (x/element :Description {} description))))))))
