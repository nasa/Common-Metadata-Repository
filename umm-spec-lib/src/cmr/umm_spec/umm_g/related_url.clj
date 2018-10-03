(ns cmr.umm-spec.umm-g.related-url
  "Contains functions for parsing UMM-G JSON related-urls into umm-lib granule model
  RelatedURLs and generating UMM-G JSON related-urls from umm-lib granule model RelatedURLs."
  (:require
   [cmr.umm.umm-collection :as umm-c])
  (:import cmr.umm.umm_collection.RelatedURL))

(defn- umm-g-related-url->RelatedURL
  "Returns the umm-lib granule model RelatedURL from the given UMM-G RelatedUrl."
  [related-url]
  (let [{:keys [URL Type Subtype Description MimeType Size]} related-url]
    (umm-c/map->RelatedURL
     {:type Type
      :sub-type Subtype
      :url URL
      :description Description
      :mime-type MimeType
      :title Description
      :size Size})))

(defn umm-g-related-urls->RelatedURLs
  "Returns the umm-lib granule model RelatedURLs from the given UMM-G RelatedUrls."
  [related-urls]
  (seq (map umm-g-related-url->RelatedURL related-urls)))

(defn RelatedURLs->umm-g-related-urls
  "Returns the UMM-G RelatedUrls from the given umm-lib granule model RelatedURLs."
  [related-urls]
  (when (seq related-urls)
    (for [related-url related-urls]
      (let [{:keys [type sub-type url description mime-type size]} related-url]
        {:URL url
         :Type type
         :Subtype sub-type
         :Description description
         :MimeType mime-type
         :Size size
         :SizeUnit (when size "NA")}))))
