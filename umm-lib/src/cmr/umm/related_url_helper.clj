(ns cmr.umm.related-url-helper
  "Contains functions for categorizing UMM related urls."
  (:require
   [clojure.string :as string]))

(def DOCUMENTATION_MIME_TYPES
  "Mime Types that indicate the RelatedURL is of documentation type"
  ["text/rtf" "text/richtext" "text/plain" "text/html" "text/example" "text/enriched"
   "text/directory" "text/csv" "text/css" "text/calendar" "application/http" "application/msword"
   "application/rtf" "application/wordperfect5.1"])

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
