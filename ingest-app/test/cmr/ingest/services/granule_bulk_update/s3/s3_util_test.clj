(ns cmr.ingest.services.granule-bulk-update.s3.s3-util-test
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.ingest.services.granule-bulk-update.s3.s3-util :as s3-util]))

(deftest validate-url-test
  (testing "validate url"
    (is (= ["s3://abc/foo" "s3://abc/bar"]
           (s3-util/validate-url "s3://abc/foo, s3://abc/bar"))))

  (testing "validate url error scenarios"
    (are3 [url-value re]
      (is (thrown-with-msg?
           Exception re (s3-util/validate-url url-value)))

      "invalid s3 link"
      "S3://abc/foo"
      #"Invalid URL value, each S3 url must start with s3://, but was S3://abc/foo"

      "invalid s3 link in multiple urls"
      "s3://abc/foo,http://example.com/bar,s3://abc/baz"
      #"Invalid URL value, each S3 url must start with s3://, but was http://example.com/bar"

      "invalid s3 links in multiple urls, only report the first error"
      "S3://abc/foo,http://example.com/bar,s3://abc/baz"
      #"Invalid URL value, each S3 url must start with s3://, but was S3://abc/foo")))
