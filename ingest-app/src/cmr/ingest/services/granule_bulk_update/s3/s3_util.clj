(ns cmr.ingest.services.granule-bulk-update.s3.s3-util
  "Contains functions to facilitate S3 url granule bulk update."
  (:require
   [clojure.string :as string]
   [cmr.common.services.errors :as errors]))

(def S3_RELATEDURL_DESCRIPTION
  "RelatedUrl description of s3 url in UMM-G granule schema"
  "This link provides direct download access via S3 to the granule.")

(defn validate-url
  "Validate the given S3 url for granule bulk update. It can be multiple urls
  separated by comma, and each url must be started with s3:// (case sensitive).
  Returns the parsed urls in a list."
  [input-url]
  (let [urls (map string/trim (string/split input-url #","))]
    (doseq [url urls]
      (when-not (string/starts-with? url "s3://")
        (errors/throw-service-errors
         :invalid-data
         [(str "Invalid URL value, each S3 url must start with s3://, but was " url)])))
    urls))
