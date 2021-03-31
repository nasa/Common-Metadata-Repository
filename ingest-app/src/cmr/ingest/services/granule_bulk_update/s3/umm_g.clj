(ns cmr.ingest.services.granule-bulk-update.s3.umm-g
  "Contains functions to update UMM-G granule metadata for s3 url bulk update.")

(def ^:private S3_RELATEDURL_TYPE
  "RelatedUrl Type of s3 url in UMM-G granule schema"
  "GET DATA VIA DIRECT ACCESS")

(def ^:private S3_RELATEDURL_DESCRIPTION
  "RelatedUrl description of s3 url in UMM-G granule schema"
  "This link provides direct download access via S3 to the granule.")

(defn- is-s3?
  "Returns true if the given related-url is an s3 url.
   An UMM-G RelatedUrl is an s3 url if its Type is GET DATA VIA DIRECT ACCESS"
  [related-url]
  (= S3_RELATEDURL_TYPE (:Type related-url)))

(defn- urls->s3-urls
  "Returns the RelatedUrls for the given s3 urls."
  [urls]
  (for [url urls]
    {:URL url :Type S3_RELATEDURL_TYPE :Description S3_RELATEDURL_DESCRIPTION}))

(defn- updated-related-urls
  "Take the RelatedUrls, update any existing s3 url with the given url-map."
  [related-urls urls]
  (let [other-urls (remove is-s3? related-urls)
        s3-urls (urls->s3-urls urls)]
    (concat s3-urls other-urls)))

(defn add-s3-url
  "Takes UMM-G record and a list of S3 urls to update.
  Returns the updated UMM-G record."
  [umm-gran urls]
  (update umm-gran :RelatedUrls #(updated-related-urls % urls)))
