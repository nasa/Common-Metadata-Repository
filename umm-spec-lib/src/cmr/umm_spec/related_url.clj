(ns cmr.umm-spec.related-url
  (:require [cmr.common.xml.gen :refer :all]
            [clojure.string :as str]))

(def related-url-types->resource-types
  "A mapping of UMM RelatedURL's type to ECHO10 OnlineResource's type.
  This list is used for generating ECHO10 OnlineResources from UMM RelatedURLs."
  {"GET DATA" "DATA ACCESS"
   "GET RELATED VISUALIZATION" "BROWSE"
   "VIEW RELATED INFORMATION" "USER SUPPORT"
   "OPENDAP DATA ACCESS" "GET DATA : OPENDAP DATA (DODS)"})

(defn downloadable-url?
  "Returns true if the related-url is downloadable"
  [related-url]
  (some #{"GET DATA"} (:Relation related-url)))

(defn downloadable-urls
  "Returns the related-urls that are downloadable"
  [related-urls]
  (filter downloadable-url? related-urls))

(defn browse-url?
  "Returns true if the related-url is browse url"
  [related-url]
  (some #{"GET RELATED VISUALIZATION"} (:Relation related-url)))

(defn browse-urls
  "Returns the related-urls that are browse urls"
  [related-urls]
  (filter browse-url? related-urls))

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
  (when-let [urls (seq (downloadable-urls related-urls))]
    [:OnlineAccessURLs
     (for [related-url urls
           :let [{:keys [Description MimeType]} related-url]
           url (:URLs related-url)]
       [:OnlineAccessURL
        [:URL url]
        [:URLDescription Description]
        [:MimeType MimeType]])]))

(defn generate-resource-urls
  "Generates the OnlineResources element of an ECHO10 XML from a UMM related urls entry."
  [related-urls]
  (when-let [urls (seq (resource-urls related-urls))]
    [:OnlineResources
     (for [related-url urls
           url (:URLs related-url)
           :let [{:keys [Description MimeType]} related-url
                 [rel] (:Relation related-url)]]
       [:OnlineResource
        [:URL url]
        [:Description Description]
        ;; There is not a well defined one to one mapping between related url type and resource type.
        ;; This default value of "UNKNOWN" is to get us by the xml schema validation.
        [:Type (get related-url-types->resource-types rel "UNKNOWN")]
        [:MimeType MimeType]])]))

(defn convert-to-bytes
  [size unit]
  (when (and size unit)
    (case (str/upper-case unit)
      ("BYTES" "B") size
      ("KILOBYTES" "KB") (* size 1024)
      ("MEGABYTES" "MB") (* size 1048576)
      ("GIGABYTES" "GB") (* size 1073741824)
      ("TERABYTES" "TB") (* size 1099511627776)
      ("PETABYTES" "PB") (* size 1125899906842624)
      nil)))


(defn generate-browse-urls
  "Generates the AssociatedBrowseImageUrls element of an ECHO10 XML from a UMM related urls entry."
  [related-urls]
  (when-let [urls (seq (browse-urls related-urls))]
    [:AssociatedBrowseImageUrls
     (for [related-url urls
           :let [{:keys [Description MimeType] {:keys [Size Unit]} :FileSize} related-url
                 file-size (convert-to-bytes Size Unit)]
           url (:URLs related-url)]
       [:ProviderBrowseUrl
        [:URL url]
        [:FileSize (when file-size (int file-size))]
        [:Description Description]
        [:MimeType MimeType]])]))
