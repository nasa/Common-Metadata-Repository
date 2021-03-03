(ns cmr.umm-spec.test.umm-to-xml-mappings.echo10
  "Tests to verify that echo10 records are generated correctly."
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as common-util :refer [are3]]
    [cmr.common.xml.parse :refer :all]
    [cmr.common.xml.simple-xpath :refer [select]]
    [cmr.umm-spec.umm-to-xml-mappings.echo10 :as echo10]))

(deftest echo10-find-first-available-distribution-price-test
  "Tests the find-first-available-distribution-price function."

  (let [actual-price-in-first-element {:ArchiveAndDistributionInformation
                                       {:FileDistributionInformation
                                         [{:FormatType "Native",
                                           :Fees "0",
                                           :Format "XLS, PDF, PNG"}
                                          {:FormatType "Native",
                                           :Format "HTML"}]}}
        actual-price-in-second-element {:ArchiveAndDistributionInformation
                                         {:FileDistributionInformation
                                           [{:FormatType "Native",
                                             :Format "XLS, PDF, PNG"}
                                            {:FormatType "Native",
                                             :Fees "0",
                                             :Format "HTML"}]}}
        actual-no-price {:ArchiveAndDistributionInformation
                          {:FileDistributionInformation
                            [{:FormatType "Native",
                              :Format "XLS, PDF, PNG"}
                             {:FormatType "Native",
                              :Format "HTML"}]}}]

    (are3 [expected-result actual-data]
      (is (= expected-result
             (echo10/find-first-available-distribution-price actual-data)))

      "Test when the price is in the first element."
      "0"
      actual-price-in-first-element

      "Tests when the price is in the second element."
      "0"
      actual-price-in-second-element

      "Tests when no price exists."
      nil
      actual-no-price)))

(deftest echo10-direct-distribution-information-test
  "Testing the echo10 direct distribution information translation from umm-c to echo10."
  (let [result (echo10/umm-c-to-echo10-xml
                 {:DirectDistributionInformation
                   {:Region "us-west-1"
                    :S3BucketAndObjectPrefixNames ["bucket1" "bucket2"]
                    :S3CredentialsAPIEndpoint "https://www.credAPIURL.org"
                    :S3CredentialsAPIDocumentationURL "https://www.credAPIDocURL.org"}})
        echo-result (first (select result "/Collection/DirectDistributionInformation"))]
    (is (and
          (= "us-west-1" (value-of echo-result "Region"))
          (= "bucket1" (first (values-at echo-result "S3BucketAndObjectPrefixName")))
          (= "bucket2" (second (values-at echo-result "S3BucketAndObjectPrefixName")))
          (= "https://www.credAPIURL.org" (value-of echo-result "S3CredentialsAPIEndpoint"))
          (= "https://www.credAPIDocURL.org" (value-of echo-result "S3CredentialsAPIDocumentationURL"))))))

(deftest echo10-direct-distribution-information-nil-test
  "Testing the echo10 direct distribution information translation from umm-c to echo10 when its nil."
  (let [result (echo10/umm-c-to-echo10-xml
                 {:DirectDistributionInformation nil})
        echo-result (first (select result "/Collection/DirectDistributionInformation"))]
    (is (= nil echo-result))))

(deftest echo10-doi-test
  "Testing the echo10 DOI translation from umm-c to echo10."
  (are3 [expected-doi-result expected-assoc-result actual-data]
    (let [result (echo10/umm-c-to-echo10-xml actual-data)
          echo-result (first (select result "/Collection/DOI"))
          assoc-result (select result "/Collection/AssociatedDOIs/AssociatedDOI")]
      (is (and
            (= expected-doi-result (value-of echo-result "DOI"))
            (= expected-assoc-result (value-of (second assoc-result) "DOI")))))

    "Testing the nominal case with all data."
    "10.5678/collectiondoi"
    "10.5678/assoc-doi2"
    {:DOI {:DOI "10.5678/collectiondoi"
           :Authority "doi.org"}
     :AssociatedDOIs [{:DOI "10.5678/assoc-doi1"
                       :Title "Title1"
                       :Authority "doi.org"}
                      {:DOI "10.5678/assoc-doi2"
                       :Authority "doi.org"}]}

    "Testing the missing collection doi sub element of doi."
    nil
    "10.5678/assoc-doi2"
    {:DOI {:Authority "doi.org"}
     :AssociatedDOIs [{:DOI "10.5678/assoc-doi1"
                       :Authority "doi.org"}
                      {:DOI "10.5678/assoc-doi2"
                       :Title "Title2"
                       :Authority "doi.org"}]}

    "Testing the missing associated dois."
    "10.5678/collectiondoi"
    nil
    {:DOI {:DOI "10.5678/collectiondoi"
           :Authority "doi.org"}
     :AssociatedDOIs nil}))
