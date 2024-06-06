(ns cmr.umm-spec.related-url
  (:require
   [clojure.string :as str]
   [cmr.common.xml.gen :refer :all]
   [cmr.umm-spec.opendap-util :as opendap-util]
   [cmr.umm-spec.related-url-titles :as related-url-titles]
   [ring.util.codec :as codec]
   [ring.util.mime-type :as mime-type]))

(def DOCUMENTATION_MIME_TYPES
  "Mime Types that indicate the RelatedURL is of documentation type"
  #{"Text/rtf" "Text/richtext" "Text/plain" "Text/html" "Text/example" "Text/enriched"
    "Text/directory" "Text/csv" "Text/css" "Text/calendar" "Application/http" "Application/msword"
    "Application/rtf" "Application/wordperfect5.1"})

(def ^:private ADDITIONAL_MIME_TYPES
  "Mime types outside the scope of ring/mime-types defaults."
  {"nc" "application/x-netcdf"
   "gml" "application/gml+xml"
   "kml" "application/vnd.google-earth.kml+xml"
   "hdf" "application/x-hdf"
   "he5" "application/xhdf5"
   "hdf5" "application/xhdf5"
   "h5" "application/xhdf5"
   "kmz" "application/vnd.google-earth.kmz"
   "dae" "image/vnd.collada+xml"})

(def ^:private DOWNLOADABLE_MIME_TYPES
  "White list of downloadable mime types"
  #{"text/csv"})

(defn infer-url-mime-type
  "Attempt to figure out mime type based off file extension."
  [url]
  (when url
    (mime-type/ext-mime-type url ADDITIONAL_MIME_TYPES)))

(defn downloadable-mime-type?
  "Mime type is downloadable if it is either in the list of approved
   mime types or if it does not match text/*"
  [mime-type]
  (when mime-type
    (or (contains? DOWNLOADABLE_MIME_TYPES mime-type)
        (not (re-matches #"^(text\/).*" mime-type)))))

(defn related-url->title
  "Return the related url title. Title is mapped by URLContentType->Type->Subtype->Title.
  if subtype is nil return the default title for that type."
  ([related-url]
   (let [{:keys [URLContentType Type Subtype]} related-url]
     (related-url->title URLContentType Type Subtype)))
  ([url-content-type type sub-type]
   (let [sub-type-titles (get-in related-url-titles/related-url-titles [url-content-type type])]
     (or (get sub-type-titles sub-type)
         (get sub-type-titles "default")))))

(defn downloadable-url?
  "Returns true if the related-url is downloadable"
  [related-url]
  (and (= "DistributionURL" (:URLContentType related-url))
       (= "GET DATA" (:Type related-url))))

(defn downloadable-urls
  "Returns the related-urls that are downloadable"
  [related-urls]
  (filter downloadable-url? related-urls))

(defn browse-url?
  "Returns true if the related-url is browse url"
  [related-url]
  (= "VisualizationURL" (:URLContentType related-url)))

(defn browse-urls
  "Returns the related-urls that are browse urls"
  [related-urls]
  (filter browse-url? related-urls))

(defn- documentation-url?
  "Returns true if the related-url is documentation url"
  [related-url]
  (= "PublicationURL" (:URLContentType related-url)))

(defn search-url?
  "Returns true if the related-url is search url"
  [related-url]
  (and (= "GET CAPABILITIES" (:Type related-url))
       (= "OpenSearch" (:Subtype related-url))))

(defn search-urls
  "Returns the related-urls that are search urls"
  [related-urls]
  (filter search-url? related-urls))

(defn resource-url?
  "Returns true if the related-url is resource url"
  [related-url]
  (not (or (downloadable-url? related-url)
           (browse-url? related-url))))

(defn resource-urls
  "Returns the related-urls that are resource urls"
  [related-urls]
  (filter resource-url? related-urls))

(def subtype->abbreviation
 "The following subtypes create a string too large to write to ECHO10 so map
 them to an abbreviation"
 {"ALGORITHM THEORETICAL BASIS DOCUMENT" "ALGORITHM THEO BASIS"
  "CALIBRATION DATA DOCUMENTATION" "CALIBRATION DATA"
  "PRODUCT QUALITY ASSESSMENT" "PRODUCT QUALITY"})

(defn- encode-url
  "Encode the query portion of the url."
  [url]
  (let [split-url (str/split url #"\?" 2)
        base-url (first split-url)
        params (second split-url)]
    (if params
      (->> params
           codec/form-decode
           codec/form-encode
           (str base-url "?"))
      url)))

(defn related-url->encoded-url
  "Ensure URL is encoded."
  [related-url]
  (if related-url
    (encode-url related-url)
    related-url))

(defn related-url->online-resource-type
 "Format the online resource type to be 'URLContentType : Type' if no subtype
 exists else 'Type : Subtype'. Can't store all 3 because of limitations in
 ECHO10"
 [related-url]
 (let [{:keys [URLContentType Type Subtype]} related-url
       Subtype (get subtype->abbreviation Subtype Subtype)]
   (if Subtype
    (str Type " : " Subtype)
    (str URLContentType " : " Type))))

(defn- related-url->link-type
  "Returns the atom link type of the related url - used for collections"
  [related-url]
  (cond
    (opendap-util/opendap-url? related-url) "service"
    (downloadable-url? related-url) "data"
    (browse-url? related-url) "browse"
    (documentation-url? related-url) "documentation"
    (search-url? related-url) "search"
    :else "metadata"))

(defn- related-url->atom-link
  "Returns the atom link of the given related-url"
  [related-url]
  (let [{url :URL {:keys [MimeType]} :GetService {:keys [Size Unit]} :GetData} related-url
        size (when (or Size Unit) (str Size Unit))]
    {:href url
     :link-type (related-url->link-type related-url)
     :mime-type MimeType
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
           :let [{:keys [Description URL]} related-url
                 MimeType (or (get-in related-url [:GetService :MimeType])
                              (get-in related-url [:GetData :MimeType]))]]
       [:OnlineAccessURL
        [:URL URL]
        [:URLDescription Description]
        (when MimeType
          [:MimeType MimeType])])]))

(defn generate-resource-urls
  "Generates the OnlineResources element of an ECHO10 XML from a UMM related urls entry."
  [related-urls]
  (when-let [urls (seq (resource-urls related-urls))]
    [:OnlineResources
     (for [related-url urls
           :let [{:keys [Description]} related-url
                 MimeType (or (get-in related-url [:GetService :MimeType])
                              (get-in related-url [:GetData :MimeType]))]]
       [:OnlineResource
        [:URL (:URL related-url)]
        [:Description Description]
        [:Type (related-url->online-resource-type related-url)]
        (when MimeType
          [:MimeType MimeType])])]))

(defn generate-browse-urls
  "Generates the AssociatedBrowseImageUrls element of an ECHO10 XML from a UMM related urls entry."
  [related-urls]
  (when-let [urls (seq (browse-urls related-urls))]
     [:AssociatedBrowseImageUrls
      (for [related-url urls
            :let [{:keys [Description]} related-url
                  MimeType (get-in related-url [:GetService :MimeType])]]
        [:ProviderBrowseUrl
         [:URL (:URL related-url)]
         [:Description Description]
         (when MimeType
           [:MimeType MimeType])])]))
