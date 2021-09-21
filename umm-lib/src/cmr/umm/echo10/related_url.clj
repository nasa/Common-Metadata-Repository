(ns cmr.umm.echo10.related-url
  "Contains functions for parsing and generating the ECHO10 OnlineResources and OnlineAccessURLs
  into UMM related urls."
  (:require [clojure.string :as string]
            [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.related-url-helper :as h]))

(def resource-type->related-url-types
  "A mapping of ECHO10 OnlineResource's type to UMM RelatedURL's type and sub-type.
   This came from a list provided by Katie on ECHO10 collections, more may need to
   be added for granules."
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
   "GET DATA : OPENDAP DATA" ["USE SERVICE API" "OPENDAP DATA"]
   "GET DATA : OPENDAP DATA (DODS)" ["USE SERVICE API" "OPENDAP DATA"]
   "USE SERVICE API : OPENDAP DATA" ["USE SERVICE API" "OPENDAP DATA"]
   "USE SERVICE API : OPENDAP DATA (DODS)" ["USE SERVICE API" "OPENDAP DATA"]
   "VIEW PROJECT HOME PAGE" ["VIEW PROJECT HOME PAGE"]
   "DMR++" ["EXTENDED METADATA" "DMR++"]
   "EXTENDED METADATA : DMR++" ["EXTENDED METADATA" "DMR++"]
   "DMR++ MISSING DATA" ["EXTENDED METADATA" "DMR++ MISSING DATA"]
   "EXTENDED METADATA : DMR++ MISSING DATA" ["EXTENDED METADATA" "DMR++ MISSING DATA"]})

(def related-url-types->resource-types
  "A mapping of UMM RelatedURL's type to ECHO10 OnlineResource's type.
  This list is used for generating ECHO10 OnlineResources from UMM RelatedURLs."
  {"GET DATA" "DATA ACCESS"
   "GET SERVICE" "SOFTWARE"
   "GET RELATED VISUALIZATION" "BROWSE"
   "VIEW RELATED INFORMATION" "USER SUPPORT"
   "USE SERVICE API" "USE SERVICE API : OPENDAP DATA"
   "VIEW PROJECT HOME PAGE" "VIEW PROJECT HOME PAGE"})

(defn xml-elem->online-resource-url
  [elem]
  (let [url (cx/string-at-path elem [:URL])
        description (cx/string-at-path elem [:Description])
        resource-type (cx/string-at-path elem [:Type])
        mime-type (cx/string-at-path elem [:MimeType])
        [type sub-type] (resource-type->related-url-types (when resource-type (string/upper-case resource-type)))
        ;; Check for opendap (case-insensitive) in OnlineResource Type when no defined type is found.
        ;; This is due to GES_DISC OnlineResource opendap could use any string that contains opendap
        ;; See CMR-2555 for details
        [type sub-type] (if (and (nil? type)
                                 resource-type
                                 (re-find #"^.*OPENDAP.*$" (string/upper-case resource-type)))
                          ["USE SERVICE API" "OPENDAP DATA"]
                          [type sub-type])]
    (c/map->RelatedURL
     {:url url
      :description description
      :title (string/trim (str description " (" resource-type ")"))
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
        description (cx/string-at-path elem [:URLDescription])
        mime-type (cx/string-at-path elem [:MimeType])
        type (if (string/includes? (string/lower-case url) "s3://")
               "GET DATA VIA DIRECT ACCESS"
               "GET DATA")]
    (c/map->RelatedURL
      {:url url
       :description description
       :title description
       :mime-type mime-type
       :type type})))

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
  (seq (map xml-elem->browse-url
            (cx/elements-at-path
              xml-struct
              [:AssociatedBrowseImageUrls :ProviderBrowseUrl]))))

(defn xml-elem->related-urls
  "Returns related-urls elements from a parsed XML structure"
  [xml-struct]
  (seq (concat (xml-elem->online-access-urls xml-struct)
               (xml-elem->online-resource-urls xml-struct)
               (xml-elem->browse-urls xml-struct))))

(defn generate-access-urls
  "Generates the OnlineAccessURLs element of an ECHO10 XML from a UMM related urls entry."
  [related-urls]
  (when-let [urls (seq (concat
                         (h/downloadable-urls related-urls)
                         (h/s3-urls related-urls)))]
    (x/element
      :OnlineAccessURLs {}
      (for [related-url urls]
        (let [{:keys [url description mime-type]} related-url]
          (x/element :OnlineAccessURL {}
                     (x/element :URL {} url)
                     (when description (x/element :URLDescription {} description))
                     (when mime-type (x/element :MimeType {} mime-type))))))))

(defn- related-url->online-resource
  "Related-url-types->resource-types does not handle the use case for matching a
  subtype, if the type and subtype are extended metadata DMR values, then encode
  them in colon format"
  [related-url]
  (let [url-type (:type related-url)
        url-subtype (:sub-type related-url)]
    (if (and (= "EXTENDED METADATA" url-type)
             (some #{url-subtype} ["DMR++" "DMR++ MISSING DATA"]))
      (str url-type " : " url-subtype)
      (get related-url-types->resource-types url-type "USER SUPPORT"))))

(defn generate-resource-urls
  "Generates the OnlineResources element of an ECHO10 XML from a UMM related urls entry."
  [related-urls]
  (when-let [urls (seq (h/resource-urls related-urls))]
    (x/element
      :OnlineResources {}
      (for [related-url urls]
        (let [{:keys [url description type mime-type]} related-url]
          (x/element :OnlineResource {}
                     (x/element :URL {} url)
                     (when description (x/element :Description {} description))
                     ;; There is not a well defined one to one mapping between related url type and resource type.
                     ;; This default value of "USER SUPPORT" is to get us by the xml schema validation.
                     (x/element :Type {} (related-url->online-resource related-url))
                     (when mime-type (x/element :MimeType {} mime-type))))))))

(defn generate-browse-urls
  "Generates the AssociatedBrowseImageUrls element of an ECHO10 XML from a UMM related urls entry."
  [related-urls]
  (when-let [urls (seq (h/browse-urls related-urls))]
    (x/element
      :AssociatedBrowseImageUrls {}
      (for [related-url urls]
        (let [{:keys [url size description mime-type]} related-url]
          (x/element :ProviderBrowseUrl {}
                     (x/element :URL {} url)
                     (when size (x/element :FileSize {} size))
                     (when description (x/element :Description {} description))
                     (when mime-type (x/element :MimeType {} mime-type))))))))
