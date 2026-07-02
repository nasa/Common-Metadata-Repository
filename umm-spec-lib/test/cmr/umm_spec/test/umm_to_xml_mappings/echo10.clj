(ns cmr.umm-spec.test.umm-to-xml-mappings.echo10
  "Tests to verify that echo10 records are generated correctly."
  (:require
    [clojure.test :refer [deftest is testing]]
    [cmr.common.util :as common-util :refer [are3]]
    [cmr.common.xml.parse :refer [value-of values-at]]
    [cmr.common.xml.simple-xpath :refer [select]]
    [cmr.umm-spec.umm-to-xml-mappings.echo10 :as echo10]))

;; Tests the find-first-available-distribution-price function.
(declare expected-result actual-data)
(deftest echo10-find-first-available-distribution-price-test

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

;; Testing the echo10 direct distribution information translation from umm-c to echo10.
(deftest echo10-direct-distribution-information-test
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

;; Testing the echo10 direct distribution information translation from umm-c to echo10 when its nil.
(deftest echo10-direct-distribution-information-nil-test
  (let [result (echo10/umm-c-to-echo10-xml
                {:DirectDistributionInformation nil})
        echo-result (first (select result "/Collection/DirectDistributionInformation"))]
    (is (= nil echo-result))))

;; Testing the echo10 DOI translation from umm-c to echo10.
(declare expected-doi-result expected-assoc-result)
(deftest echo10-doi-test
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

;; Testing the echo10 use constraint translation from umm-c to echo10.
(deftest echo10-use-constraints-test
  (testing "echo10 use constraints description test"
    (let [actual-data {:UseConstraints {:Description "Description"}}
          result (echo10/umm-c-to-echo10-xml actual-data)
          echo-result (first (select result "/Collection/UseConstraints"))]
      (is (= "Description" (value-of echo-result "Description")))
      (is (= nil (value-of echo-result "FreeAndOpenData")))))

  (testing "echo10 use constraints FreeAndOpenData test true"
    (let [actual-data {:UseConstraints {:FreeAndOpenData true}}
          result (echo10/umm-c-to-echo10-xml actual-data)
          echo-result (first (select result "/Collection/UseConstraints"))]
      (is (= true (Boolean/valueOf (value-of echo-result "FreeAndOpenData"))))))

  (testing "echo10 use constraints FreeAndOpenData test false"
    (let [actual-data {:UseConstraints {:FreeAndOpenData false}}
          result (echo10/umm-c-to-echo10-xml actual-data)
          echo-result (first (select result "/Collection/UseConstraints"))]
      (is (= false (Boolean/valueOf (value-of echo-result "FreeAndOpenData"))))))

  (testing "echo10 use constraints LicenseURL test"
    (let [actual-data {:UseConstraints {:LicenseURL {:Linkage "https://someurl.com"
                                                     :Protocol "https"
                                                     :ApplicationProfile "profile"
                                                     :Name "License URL"
                                                     :Description "License URL Description"
                                                     :Function "information"
                                                     :MimeType "text/html"}}}
          result (echo10/umm-c-to-echo10-xml actual-data)
          echo-result (first (select result "/Collection/UseConstraints"))]
      (is (= "https://someurl.com" (value-of echo-result "LicenseURL/URL")))
      (is (= "License URL" (value-of echo-result "LicenseURL/Type")))
      (is (= "License URL Description" (value-of echo-result "LicenseURL/Description")))
      (is (= "text/html" (value-of echo-result "LicenseURL/MimeType")))))

  (testing "echo10 use constraints License Text test"
    (let [actual-data {:UseConstraints {:LicenseText "License Text"}}
          result (echo10/umm-c-to-echo10-xml actual-data)
          echo-result (first (select result "/Collection/UseConstraints"))]
      (is (= "License Text" (value-of echo-result "LicenseText")))))

  (testing "echo10 use constraints nil"
    (let [actual-data {}
          result (echo10/umm-c-to-echo10-xml actual-data)
          echo-result (first (select result "/Collection/UseConstraints"))]
      (is (= nil echo-result))))

  (testing "echo10 use constraints Description and LicenseURL test"
    (let [actual-data {:UseConstraints {:Description "Description"
                                        :LicenseURL {:Linkage "https://someurl.com"
                                                     :Protocol "https"
                                                     :ApplicationProfile "profile"
                                                     :Name "License URL"
                                                     :Description "License URL Description"
                                                     :Function "information"
                                                     :MimeType "text/html"}}}
          result (echo10/umm-c-to-echo10-xml actual-data)
          echo-result (first (select result "/Collection/UseConstraints"))]
      (is (and (= "Description" (value-of echo-result "Description"))
               (= "https://someurl.com" (value-of echo-result "LicenseURL/URL"))
               (= "License URL" (value-of echo-result "LicenseURL/Type"))
               (= "License URL Description" (value-of echo-result "LicenseURL/Description"))
               (= "text/html" (value-of echo-result "LicenseURL/MimeType"))))))

  (testing "echo10 use constraints Description and LicenseText test"
    (let [actual-data {:UseConstraints {:Description "Description"
                                        :LicenseText "License Text"}}
          result (echo10/umm-c-to-echo10-xml actual-data)
          echo-result (first (select result "/Collection/UseConstraints"))]
      (is (and (= "Description" (value-of echo-result "Description"))
               (= "License Text" (value-of echo-result "LicenseText")))))))

(deftest generate-quality-xml-test
  (testing "1. Returns nil when the root :Quality key is entirely absent"
    (let [input {:EntryId "NASA-METADATA-1"}]
      (is (nil? (echo10/generate-quality input)))))

  (testing "2. Returns nil if :Summary is missing or empty (schema violation protection)"
    (let [input {:Quality {:QualityContentDetails {:Strengths "Good data."}}}]
      (is (nil? (echo10/generate-quality input)))))

  (testing "3. Generates a valid XML vector sequence structure with all elements populated"
    (let [input {:Quality {:Summary "Overview statement."
                           :QualityContentDetails {:Strengths "High precision."
                                                   :Limitations "Cloud interference."
                                                   :KnownIssues "Calibration drift."
                                                   :Other "Flags attached."}}}
          result (echo10/generate-quality input)]

      ;; Verify root element tag
      (is (= :Quality (first result)))

      ;; Verify required Summary node position via index ordering
      (is (= [:Summary "Overview statement."] (nth result 1)))

      ;; Verify structural placement of child blocks
      (let [details (nth result 2)]
        (is (= :QualityContentDetails (first details)))
        (is (some #{[:Strengths "High precision."]} details))
        (is (some #{[:Limitations "Cloud interference."]} details))
        (is (some #{[:KnownIssues "Calibration drift."]} details))
        (is (some #{[:Other "Flags attached."]} details)))))

  (testing "4. Omit QualityContentDetails element completely if the map is empty"
    (let [input {:Quality {:Summary "Overview only version."
                           :QualityContentDetails {}}}
          result (echo10/generate-quality input)]
      (is (= [:Summary "Overview only version."] (nth result 1)))))

  (testing "5. Safely drop sub-fields mapped to nil or empty string content"
    (let [input  {:Quality {:Summary "Overview statement."
                            :QualityContentDetails {:Strengths "Excellent reliability."
                                                    :Limitations ""
                                                    :KnownIssues nil}}}
          result (echo10/generate-quality input)
          details (nth result 2)]
      (is (some #{[:Strengths "Excellent reliability."]} details))
      (is (not (some (fn [x] (and (vector? x) (= :Limitations (first x)))) details)))
      (is (not (some (fn [x] (and (vector? x) (= :KnownIssues (first x)))) details))))))
