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

(def DOCUMENTATION_MIME_TYPES
  "Mime Types that indicate the RelatedURL is of documentation type"
  #{"Text/rtf" "Text/richtext" "Text/plain" "Text/html" "Text/example" "Text/enriched"
   "Text/directory" "Text/csv" "Text/css" "Text/calendar" "Application/http" "Application/msword"
   "Application/rtf" "Application/wordperfect5.1"})

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

(defn- documentation-url?
  "Returns true if the related-url is documentation url"
  [related-url]
  (contains? DOCUMENTATION_MIME_TYPES (:MimeType related-url)))

(defn resource-url?
  "Returns true if the related-url is resource url"
  [related-url]
  (not (or (downloadable-url? related-url)
           (browse-url? related-url))))

(defn resource-urls
  "Returns the related-urls that are resource urls"
  [related-urls]
  (filter resource-url? related-urls))

(defn- related-url->link-type
  "Returns the atom link type of the related url"
  [related-url]
  (cond
    (downloadable-url? related-url) "data"
    (browse-url? related-url) "browse"
    (documentation-url? related-url) "documentation"
    :else "metadata"))

(defn- related-url->atom-link
  "Returns the atom link of the given related-url"
  [related-url]
  (let [{urls :URLs title :Title mime-type :MimeType {:keys [Size Unit]} :FileSize} related-url
        size (when (or Size Unit) (str Size Unit))]
    ;; The current UMM JSON RelatedUrlType is flawed in that there can be multiple URLs,
    ;; but only a single Title, MimeType and FileSize. This model doesn't make sense.
    ;; Talked to Erich and he said that we are going to change the model.
    ;; So for now, we make the assumption that there is only one URL in each RelatedUrlType.
    {:href (first urls)
     :link-type (related-url->link-type related-url)
     :title title
     :mime-type mime-type
     :size size}))

(defn atom-links
  "Returns a sequence of atom links for the given related urls"
  [related-urls]
  (map related-url->atom-link related-urls))

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
