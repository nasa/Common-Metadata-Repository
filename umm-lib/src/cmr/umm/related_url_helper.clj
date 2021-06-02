(ns cmr.umm.related-url-helper
  "Contains functions for categorizing UMM related urls."
  (:require
   [clojure.string :as string]
   [ring.util.codec :as codec]
   [ring.util.mime-type :as mime-type]))

(def DOCUMENTATION_MIME_TYPES
  "Mime Types that indicate the RelatedURL is of documentation type"
  ["text/rtf" "text/richtext" "text/plain" "text/html" "text/example" "text/enriched"
   "text/directory" "text/csv" "text/css" "text/calendar" "application/http" "application/msword"
   "application/rtf" "application/wordperfect5.1"])

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

(defn downloadable-url?
  "Returns true if the related-url is downloadable"
  [related-url]
  (= "GET DATA" (:type related-url)))

(defn downloadable-urls
  "Returns the related-urls that are downloadable"
  [related-urls]
  (filter downloadable-url? related-urls))

(defn s3-url?
  "Returns true if the related-url is a s3 URL"
  [related-url]
  (= "GET DATA VIA DIRECT ACCESS" (:type related-url)))

(defn s3-urls
  "Returns the related-urls that are s3 URLs"
  [related-urls]
  (filter s3-url? related-urls))

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
  (when-let [mime-type (:mime-type related-url)]
    (some #{(string/lower-case mime-type)} DOCUMENTATION_MIME_TYPES)))

(defn documentation-urls
  "Returns the related-urls that are documentation urls"
  [related-urls]
  (filter documentation-url? related-urls))

(defn metadata-url?
  "Returns true if the related-url is metadata url"
  [related-url]
  (not (or (downloadable-url? related-url)
           (s3-url? related-url)
           (browse-url? related-url)
           (documentation-url? related-url))))

(defn metadata-urls
  "Returns the related-urls that are metadata urls"
  [related-urls]
  (filter metadata-url? related-urls))

(defn opendap-url?
  "Returns true if the related-url is OPeNDAP url"
  [related-url]
  (and (= "USE SERVICE API" (:type related-url))
       (= "OPENDAP DATA" (:sub-type related-url))))

(defn resource-url?
  "Returns true if the related-url is resource url"
  [related-url]
  (not (or (downloadable-url? related-url)
           (s3-url? related-url)
           (browse-url? related-url))))

(defn resource-urls
  "Returns the related-urls that are resource urls"
  [related-urls]
  (filter resource-url? related-urls))

(defn- related-url->link-type
  "Returns the atom link type of the related url - used for granules"
  [related-url]
  (cond
    (downloadable-url? related-url) "data"
    (s3-url? related-url) "s3"
    (opendap-url? related-url) "service"
    (browse-url? related-url) "browse"
    (documentation-url? related-url) "documentation"
    :else "metadata"))

(defn- related-url->atom-link
  "Returns the atom link of the given related-url"
  [related-url]
  (let [{:keys [url title mime-type size]} related-url]
    {:href url
     :link-type (related-url->link-type related-url)
     :title title
     :mime-type mime-type
     :size size}))

(defn atom-links
  "Returns a sequence of atom links for the given related urls"
  [related-urls]
  (map related-url->atom-link related-urls))

(defn- encode-url
  "Encode the query portion of the url."
  [url]
  (let [split-url (string/split url #"\?" 2)
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
