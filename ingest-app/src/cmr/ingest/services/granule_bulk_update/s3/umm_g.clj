(ns cmr.ingest.services.granule-bulk-update.s3.umm-g
  "Contains functions to update UMM-G granule metadata for s3 url bulk update."
  (:require
   [cmr.ingest.services.granule-bulk-update.s3.s3-util :as s3-util]))

(def ^:private S3_RELATEDURL_TYPE
  "RelatedUrl Type of s3 url in UMM-G granule schema"
  "GET DATA VIA DIRECT ACCESS")

(defn- is-s3?
  "Returns true if the given related-url is an s3 url.
   An UMM-G RelatedUrl is an s3 url if its Type is GET DATA VIA DIRECT ACCESS"
  [related-url]
  (= S3_RELATEDURL_TYPE (:Type related-url)))

(defn- urls->s3-urls
  "Returns the RelatedUrls for the given s3 urls."
  [urls]
  (for [url urls]
    {:URL url :Type S3_RELATEDURL_TYPE :Description s3-util/S3_RELATEDURL_DESCRIPTION}))

(defn- updated-related-urls
  "Take the RelatedUrls, replace any existing s3 urls with the given urls."
  [related-urls urls]
  (let [other-urls (remove is-s3? related-urls)
        s3-urls (urls->s3-urls (distinct urls))]
    (concat s3-urls other-urls)))

(defn- appended-related-urls
  "Take the RelatedUrls, add any unique s3 urls, ignoring already present s3 urls
  returning a new list of related-urls."
  [related-urls urls]
  (let [s3-resources (filter is-s3? related-urls)
        new-s3-urls (clojure.set/difference (set urls) (set (map :URL s3-resources)))
        new-s3-resources (urls->s3-urls new-s3-urls)]
    (concat related-urls new-s3-resources)))

(defn update-s3-url
  "Takes UMM-G record and a list of S3 urls to update.
  Returns the updated UMM-G record."
  [_context umm-gran urls]
  (update umm-gran :RelatedUrls #(updated-related-urls % urls)))

(defn append-s3-url
  "Takes UMM-G record and a list of S3 urls to append.
  Returns the updated UMM-G record."
  [_context umm-gran urls]
  (update umm-gran :RelatedUrls #(appended-related-urls % urls)))
