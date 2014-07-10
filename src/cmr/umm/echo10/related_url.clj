(ns cmr.umm.echo10.related-url
  "Contains functions for parsing and generating the ECHO10 OnlineResources and OnlineAccessURLs
  into UMM related urls."
  (:require [clojure.string :as s]
            [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]))

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

(def related-url-types->resource-types
  "A mapping of UMM RelatedURL's type to ECHO10 OnlineResource's type.
  This list is used for generating ECHO10 OnlineResources from UMM RelatedURLs."
  {"GET DATA" "STATIC URL"
   "GET RELATED VISUALIZATION" "BROWSE"})

(def DOCUMENTATION_MIME_TYPES
  ["Text/rtf" "Text/richtext" "Text/plain" "Text/html" "Text/example" "Text/enriched"
   "Text/directory" "Text/csv" "Text/css" "Text/calendar" "Application/http" "Application/msword"
   "Application/rtf" "Application/wordperfect5.1"])

(defn xml-elem->online-resource-url
  [elem]
  (let [url (cx/string-at-path elem [:URL])
        description (cx/string-at-path elem [:Description])
        resource-type (cx/string-at-path elem [:Type])
        mime-type (cx/string-at-path elem [:MimeType])
        [type sub-type] (resource-type->related-url-types (when resource-type (s/upper-case resource-type)))]
    (c/map->RelatedURL
      {:url url
       :description description
       :title (s/trim (str description " (" resource-type ")"))
       :type type
       :sub-type sub-type
       :mime-type mime-type})))

(defn- xml-elem->online-resource-urls
  "Returns online-resource-urls elements from a parsed XML structure"
  [xml-struct]
  (let [urls (map xml-elem->online-resource-url
                  (cx/elements-at-path
                    xml-struct
                    [:OnlineResources :OnlineResource]))]
    (when-not (empty? urls)
      urls)))

(defn xml-elem->online-access-url
  [elem]
  (let [url (cx/string-at-path elem [:URL])
        description (cx/string-at-path elem [:URLDescription])]
    (c/map->RelatedURL
      {:url url
       :description description
       :title description
       :type "GET DATA"})))

(defn- xml-elem->online-access-urls
  "Returns online-access-urls elements from a parsed XML structure"
  [xml-struct]
  (let [urls (map xml-elem->online-access-url
                  (cx/elements-at-path
                    xml-struct
                    [:OnlineAccessURLs :OnlineAccessURL]))]
    (when-not (empty? urls)
      urls)))

(defn xml-elem->browse-url
  [elem]
  (let [url (cx/string-at-path elem [:URL])
        size (cx/long-at-path elem [:FileSize])
        description (cx/string-at-path elem [:Description])
        mime-type (cx/string-at-path elem [:MimeType])]
    (c/map->RelatedURL
      {:url url
       :size size
       :description description
       :title description
       :mime-type mime-type
       :type "GET RELATED VISUALIZATION"})))

(defn- xml-elem->browse-urls
  "Returns browse-urls elements from a parsed XML structure"
  [xml-struct]
  (let [urls (map xml-elem->browse-url
                  (cx/elements-at-path
                    xml-struct
                    [:AssociatedBrowseImageUrls :ProviderBrowseUrl]))]
    (when-not (empty? urls)
      urls)))

(defn xml-elem->related-urls
  "Returns related-urls elements from a parsed XML structure"
  [xml-struct]
  (seq (concat (xml-elem->online-access-urls xml-struct)
               (xml-elem->online-resource-urls xml-struct)
               (xml-elem->browse-urls xml-struct))))

(defn downloadable-url?
  "Returns true if the related-url is downloadable"
  [related-url]
  (= "GET DATA" (:type related-url)))

(defn downloadable-urls
  "Returns the related-urls that are downloadable"
  [related-urls]
  (filter downloadable-url? related-urls))

(defn browse-url?
  "Returns true if the related-url is browse url"
  [related-url]
  (= "GET RELATED VISUALIZATION" (:type related-url)))

(defn browse-urls
  "Returns the related-urls that are browse urls"
  [related-urls]
  (filter browse-url? related-urls))

(defn documentation-url?
  "Returns true if the related-url is documentation url"
  [related-url]
  (some #{(:mime-type related-url)} DOCUMENTATION_MIME_TYPES))

(defn documentation-urls
  "Returns the related-urls that are documentation urls"
  [related-urls]
  (filter documentation-url? related-urls))

(defn metadata-url?
  "Returns true if the related-url is metadata url"
  [related-url]
  (not (or (downloadable-url? related-url)
           (browse-url? related-url)
           (documentation-url? related-url))))

(defn metadata-urls
  "Returns the related-urls that are metadata urls"
  [related-urls]
  (filter metadata-url? related-urls))

(defn resource-url?
  "Returns true if the related-url is resource url"
  [related-url]
  (not (or (downloadable-url? related-url)
           (browse-url? related-url))))

(defn resource-urls
  "Returns the related-urls that are resource urls"
  [related-urls]
  (filter resource-url? related-urls))

(defn generate-access-urls
  "Generates the OnlineAccessURLs element of an ECHO10 XML from a UMM related urls entry."
  [related-urls]
  (let [urls (downloadable-urls related-urls)]
    (when-not (empty? urls)
      (x/element
        :OnlineAccessURLs {}
        (for [related-url urls]
          (let [{:keys [url description mime-type]} related-url]
            (x/element :OnlineAccessURL {}
                       (x/element :URL {} url)
                       (when description (x/element :URLDescription {} description))
                       (when mime-type (x/element :MimeType {} mime-type)))))))))

(defn generate-resource-urls
  "Generates the OnlineResources element of an ECHO10 XML from a UMM related urls entry."
  [related-urls]
  (let [urls (resource-urls related-urls)]
    (when-not (empty? urls)
      (x/element
        :OnlineResources {}
        (for [related-url urls]
          (let [{:keys [url description type mime-type]} related-url]
            (x/element :OnlineResource {}
                       (x/element :URL {} url)
                       (x/element :Type {} (related-url-types->resource-types type))
                       (when description (x/element :URLDescription {} description))
                       (when mime-type (x/element :MimeType {} mime-type)))))))))

(defn generate-browse-urls
  "Generates the AssociatedBrowseImageUrls element of an ECHO10 XML from a UMM related urls entry."
  [related-urls]
  (let [urls (browse-urls related-urls)]
    (when-not (empty? urls)
      (x/element
        :AssociatedBrowseImageUrls {}
        (for [related-url urls]
          (let [{:keys [url size description mime-type]} related-url]
            (x/element :ProviderBrowseUrl {}
                       (x/element :URL {} url)
                       (when size (x/element :FileSize {} size))
                       (when description (x/element :Description {} description))
                       (when mime-type (x/element :MimeType {} mime-type)))))))))
