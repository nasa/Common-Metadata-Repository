(ns cmr.umm-spec.test.umm-to-xml-mappings.dif10
  "Tests to verify that dif10 records are generated correctly."
  (:require
    [clojure.test :refer :all]
    [cmr.umm-spec.umm-to-xml-mappings.dif10 :as dif10]
    [cmr.common.xml.parse :refer :all]
    [cmr.common.xml.simple-xpath :refer [select]]))

(deftest dif10-direct-distribution-information-test
  "Testing the dif10 direct distribution information translation from umm-c to dif10."
  (let [result (dif10/umm-c-to-dif10-xml
                 {:DirectDistributionInformation
                   {:Region "us-west-1"
                    :S3BucketAndObjectPrefixNames ["bucket1" "bucket2"]
                    :S3CredentialsAPIEndpoint "https://www.credAPIURL.org"
                    :S3CredentialsAPIDocumentationURL "https://www.credAPIDocURL.org"}})
        diff-result (first (select result "/DIF/DirectDistributionInformation"))]
    (is (and
          (= "us-west-1" (value-of diff-result "Region"))
          (= "bucket1" (first (values-at diff-result "S3BucketAndObjectPrefixName")))
          (= "bucket2" (second (values-at diff-result "S3BucketAndObjectPrefixName")))
          (= "https://www.credAPIURL.org" (value-of diff-result "S3CredentialsAPIEndpoint"))
          (= "https://www.credAPIDocURL.org" (value-of diff-result "S3CredentialsAPIDocumentationURL"))))))

(deftest dif10-direct-distribution-information-nil-test
  "Testing the dif10 direct distribution information translation from umm-c to dif10 when its nil."
  (let [result (dif10/umm-c-to-dif10-xml
                 {:DirectDistributionInformation nil})
        diff-result (first (select result "/DIF/DirectDistributionInformation"))]
    (is (= diff-result nil))))
