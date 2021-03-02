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

(deftest dif10-doi-and-associated-doi-test
  "Testing the dif10 doi and associated doi translation from umm-c to dif10."
  (let [result (dif10/umm-c-to-dif10-xml
                 {:DOI {:DOI "10.5067/IAGYM8Q26QRE"
                        :Authority "https://doi.org"}
                  :AssociatedDOIs [{:DOI "10.5678/assoc-doi-1"
                                    :Title "Title1"
                                    :Authority "doi.org"}
                                   {:DOI "10.5678/assoc-doi-2"
                                    :Title "Title2"
                                    :Authority "doi.org"}]})
        doi (first (select result "/DIF/Dataset_Citation/Persistent_Identifier"))
        assoc-dois (select result "/DIF/Associated_DOIs")]
    (is (and
          (= "10.5067/IAGYM8Q26QRE" (value-of doi "Identifier"))
          (= "10.5678/assoc-doi-2" (value-of (second assoc-dois) "DOI"))
          (= "Title2" (value-of (second assoc-dois) "Title"))
          (= "doi.org" (value-of (second assoc-dois) "Authority"))))))

(deftest dif10-doi-and-associated-nil-doi-test
  "Testing the dif10 doi and associated doi translation from umm-c to dif10 when its nil."
  (let [result (dif10/umm-c-to-dif10-xml
                 {:DOI nil
                  :AssoiatedDOIs nil})
        doi (first (select result "/DIF/Dataset_Citation/Persistent_Identifier"))
        assoc-dois (select result "/DIF/Associated_DOIs")]
    (is (= doi nil)
        (= assoc-dois nil))))
