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
    (is (= "us-west-1" (value-of diff-result "Region")))
    (is (= "bucket1" (first (values-at diff-result "S3BucketAndObjectPrefixName"))))
    (is (= "bucket2" (second (values-at diff-result "S3BucketAndObjectPrefixName"))))
    (is (= "https://www.credAPIURL.org" (value-of diff-result "S3CredentialsAPIEndpoint")))
    (is (= "https://www.credAPIDocURL.org" (value-of diff-result "S3CredentialsAPIDocumentationURL")))))

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
    (is (= "10.5067/IAGYM8Q26QRE" (value-of doi "Identifier")))
    (is (= "10.5678/assoc-doi-2" (value-of (second assoc-dois) "DOI")))
    (is (= "Title2" (value-of (second assoc-dois) "Title")))
    (is (= "doi.org" (value-of (second assoc-dois) "Authority")))))

(deftest dif10-doi-and-associated-nil-doi-test
  "Testing the dif10 doi and associated doi translation from umm-c to dif10 when its nil."
  (let [result (dif10/umm-c-to-dif10-xml
                 {:DOI nil
                  :AssoiatedDOIs nil})
        doi (first (select result "/DIF/Dataset_Citation/Persistent_Identifier"))
        assoc-dois (select result "/DIF/Associated_DOIs")]
    (is (= doi nil))
    (is (= assoc-dois nil))))

(deftest dif10-use-constraints-test
  "Testing the dif10 use constraint translation from umm-c to dif10."

  (testing "dif10 use constraints description test"
    (let [actual-data {:UseConstraints {:Description "Description"}}
          result (dif10/umm-c-to-dif10-xml actual-data)
          use-constraints (first (select result "/DIF/Use_Constraints"))]
      (is (= "Description" (value-of use-constraints "Description")))
      (is (= nil (value-of use-constraints "Free_And_Open_Data")))))

  (testing "dif10 use constraints Free_And_Open_Data test"
    (let [actual-data {:UseConstraints {:FreeAndOpenData true}}
          result (dif10/umm-c-to-dif10-xml actual-data)
          use-constraints (first (select result "/DIF/Use_Constraints"))]
      (is (= true (Boolean/valueOf (value-of use-constraints "Free_And_Open_Data"))))))

  (testing "dif10 use constraints LicenseURL test"
    (let [actual-data {:UseConstraints {:LicenseURL {:Linkage "https://someurl.com"
                                                     :Protocol "https"
                                                     :ApplicationProfile "profile"
                                                     :Name "License URL"
                                                     :Description "License URL Description"
                                                     :Function "information"
                                                     :MimeType "text/html"}}}
          result (dif10/umm-c-to-dif10-xml actual-data)
          use-constraints (first (select result "/DIF/Use_Constraints"))]
      (is (= "https://someurl.com" (value-of use-constraints "License_URL/URL")))
      (is (= "License URL" (value-of use-constraints "License_URL/Title")))
      (is (= "License URL Description" (value-of use-constraints "License_URL/Description")))
      (is (= "text/html" (value-of use-constraints "License_URL/Mime_Type")))))

  (testing "dif10 use constraints License Text test"
    (let [actual-data {:UseConstraints {:LicenseText "License Text"}}
          result (dif10/umm-c-to-dif10-xml actual-data)
          use-constraints (first (select result "/DIF/Use_Constraints"))]
      (is (= "License Text" (value-of use-constraints "License_Text")))))

  (testing "dif10 use constraints nil"
    (let [actual-data {}
          result (dif10/umm-c-to-dif10-xml actual-data)
          use-constraints (first (select result "/DIF/Use_Constraints"))]
      (is (= nil use-constraints))))

  (testing "dif10 use constraints Description and LicenseURL test"
    (let [actual-data {:UseConstraints {:Description "Description"
                                        :LicenseURL {:Linkage "https://someurl.com"
                                                     :Protocol "https"
                                                     :ApplicationProfile "profile"
                                                     :Name "License URL"
                                                     :Description "License URL Description"
                                                     :Function "information"
                                                     :MimeType "text/html"}}}
          result (dif10/umm-c-to-dif10-xml actual-data)
          use-constraints (first (select result "/DIF/Use_Constraints"))]
      (is (= "Description" (value-of use-constraints "Description")))
      (is (= "https://someurl.com" (value-of use-constraints "License_URL/URL")))
      (is (= "License URL" (value-of use-constraints "License_URL/Title")))
      (is (= "License URL Description" (value-of use-constraints "License_URL/Description")))
      (is (= "text/html" (value-of use-constraints "License_URL/Mime_Type")))))

 (testing "dif10 use constraints Description and LicenseText test"
   (let [actual-data {:UseConstraints {:Description "Description"
                                       :LicenseText "License Text"}}
         result (dif10/umm-c-to-dif10-xml actual-data)
         use-constraints (first (select result "/DIF/Use_Constraints"))]
     (is (= "Description" (value-of use-constraints "Description")))
     (is (= "License Text" (value-of use-constraints "License_Text"))))))
