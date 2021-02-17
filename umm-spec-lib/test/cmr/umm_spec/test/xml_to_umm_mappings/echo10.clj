(ns cmr.umm-spec.test.xml-to-umm-mappings.echo10
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.util :as common-util :refer [are3]]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.xml-to-umm-mappings.echo10 :as echo10]
   [cmr.umm-spec.xml-to-umm-mappings.echo10.data-contact :as contact]))

(deftest echo10-contact-role-test
  (testing "ECHO10 Contact with invalid role and default applied"
    ;; Test that when the contact role is invalid, the data center defaults to the correct role
    (let [xml "<Collection>
                <Contacts>
                 <Contact>
                  <Role>USER SERVICES</Role>
                  <OrganizationName>LPDAAC</OrganizationName>
                 </Contact>
                </Contacts>
               </Collection>"
          data-centers (contact/parse-data-centers xml true)]
      (is (= [contact/default-data-center-role] (:Roles (first data-centers)))))
   (testing "ECHO10 Contact with invalid role and no default applied"
     (let [xml "<Collection>
                 <Contacts>
                  <Contact>
                   <Role>USER SERVICES</Role>
                   <OrganizationName>LPDAAC</OrganizationName>
                  </Contact>
                 </Contacts>
                </Collection>"
           data-centers (contact/parse-data-centers xml false)]
       (is (= [] (:Roles (first data-centers)))))
    (testing "ECHO10 OrganizationName is truncated to comply with ShortName's 85 character limit"
      (let [organization-name "TheNeverEndingOrganizationNameCheckItOutItJustKeepsGoingAndGoingLikeTheEngerigizerBunnyThisIsJustImpressiveWhatAGreatOrganizationName"
            xml (str "<Collection>
                       <Contacts>
                        <Contact>
                         <Role>USER SERVICES</Role>
                         <OrganizationName>" organization-name "</OrganizationName>
                        </Contact>
                       </Contacts>
                      </Collection>")
            data-center (first (contact/parse-data-centers xml true))]
        (is (= organization-name (:LongName data-center)))
        (is (= (subs organization-name 0 85)
               (:ShortName data-center)))
        (is (= 85 (count (:ShortName data-center))))
        (is (< 85 (count (:LongName data-center)))))))))

(deftest echo10-multiple-data-formats-test
  "This tests the echo 10 parse-archive-dist-info function"

  (let [base-record (slurp (io/resource "example-data/echo10/artificial_test_data.xml"))
        expected-single-dataformat-without-price {:FileDistributionInformation [{:FormatType "Native",
                                                                                 :Format "XLS, PDF, PNG"}]}
        actual-single-dataformat-without-price (-> base-record
                                                   (string/replace #"<Price>0</Price>" "")
                                                   (string/replace #"<DataFormat>HTML</DataFormat>" ""))
        expected-multi-dataformat-without-price {:FileDistributionInformation [{:FormatType "Native",
                                                                                :Format "XLS, PDF, PNG"}
                                                                               {:FormatType "Native",
                                                                                :Format "HTML"}]}
        actual-multi-dataformat-without-price (-> base-record
                                                  (string/replace #"<Price>0</Price>" ""))
        expected-single-dataformat-with-price {:FileDistributionInformation [{:FormatType "Native",
                                                                              :Fees "0",
                                                                              :Format "XLS, PDF, PNG"}]}
        actual-single-dataformat-with-price (-> base-record
                                                (string/replace #"<DataFormat>HTML</DataFormat>" ""))
        expected-multi-dataformat-with-price {:FileDistributionInformation [{:FormatType "Native",
                                                                             :Fees "0",
                                                                             :Format "XLS, PDF, PNG"}
                                                                            {:FormatType "Native",
                                                                             :Fees "0",
                                                                             :Format "HTML"}]}
        expected-price-without-dataformat {:FileDistributionInformation [{:FormatType "Native",
                                                                          :Fees "0",
                                                                          :Format "Not provided"}]}
        actual-price-without-dataformat (-> base-record
                                            (string/replace #"<DataFormat>.*</DataFormat>" ""))
        actual-no-price-no-dataformat (-> base-record
                                          (string/replace #"<Price>.*</Price>" "")
                                          (string/replace #"<DataFormat>.*</DataFormat>" ""))]

    (are3 [expected-result test-string]
      (is (= expected-result
             (echo10/parse-archive-dist-info test-string)))

      "Single DataFormat test without Price"
      expected-single-dataformat-without-price
      actual-single-dataformat-without-price

      "Multi DataFormat test without Price"
      expected-multi-dataformat-without-price
      actual-multi-dataformat-without-price

      "Single DataFormat test with Price"
      expected-single-dataformat-with-price
      actual-single-dataformat-with-price

      "Multi DataFormat test with Price"
      expected-multi-dataformat-with-price
      base-record

      "Price without DataFormat"
      expected-price-without-dataformat
      actual-price-without-dataformat

      "No Price and no DataFormats"
      nil
      actual-no-price-no-dataformat)))

(deftest echo10-direct-distribution-information-test
  "Testing the direct distribution information translation from echo10 to UMM-C."
  (is (= {:Region "us-west-1"
          :S3BucketAndObjectPrefixNames ["bucket1" "bucket2"]
          :S3CredentialsAPIEndpoint "https://www.credAPIURL.org"
          :S3CredentialsAPIDocumentationURL "https://www.credAPIDocURL.org"}
         (:DirectDistributionInformation
           (#'echo10/parse-echo10-xml (lkt/setup-context-for-test)
                                      "<Collection>
                                         <DirectDistributionInformation>
                                           <Region>us-west-1</Region>
                                           <S3BucketAndObjectPrefixName>bucket1</S3BucketAndObjectPrefixName>
                                           <S3BucketAndObjectPrefixName>bucket2</S3BucketAndObjectPrefixName>
                                           <S3CredentialsAPIEndpoint>https://www.credAPIURL.org</S3CredentialsAPIEndpoint>
                                           <S3CredentialsAPIDocumentationURL>https://www.credAPIDocURL.org</S3CredentialsAPIDocumentationURL>
                                         </DirectDistributionInformation>
                                       </Collection>"
                                      true)))))

(deftest echo10-direct-distribution-information-nil-test
  "Testing the direct distribution information translation from echo10 to UMM-C when its nil."
  (is (= nil
         (:DirectDistributionInformation
           (#'echo10/parse-echo10-xml (lkt/setup-context-for-test)
                                      "<Collection>
                                       </Collection>"
                                      true)))))
