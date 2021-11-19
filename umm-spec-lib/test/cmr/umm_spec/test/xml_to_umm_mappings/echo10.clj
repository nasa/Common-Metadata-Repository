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

(deftest echo10-doi-translation-test
  "This tests the echo 10 DOI translation from echo10 to UMM-C."
  (let [base-record (slurp (io/resource "example-data/echo10/artificial_test_data.xml"))]

    (are3 [expected-doi-result expected-associated-doi-result test-string]
      (let [result (#'echo10/parse-echo10-xml (lkt/setup-context-for-test) test-string true)]
        (is (= expected-doi-result
               (:DOI result))
            (= expected-associated-doi-result
               (:AssociatedDOIs result))))

      "Test the nominal success case."
      {:DOI "10.5067/IAGYM8Q26QRE"
       :Authority "https://doi.org/"}
      [{:DOI "10.5678/assoc-doi-1"
        :Title "Title1"
        :Authority "doi.org"}
       {:DOI "10.5678/assoc-doi-2"
        :Title "Title2"
        :Authority "doi.org"}]
      (-> base-record
          (string/replace #"</DOI>"
                          "</DOI>
                           <AssociatedDOIs>
                             <AssociatedDOI>
                               <DOI>10.5678/assoc-doi-1</DOI>
                               <Title>Title1</Title>
                               <Authority>doi.org</Authority>
                             </AssociatedDOI>
                             <AssociatedDOI>
                               <DOI>10.5678/assoc-doi-2</DOI>
                               <Title>Title2</Title>
                               <Authority>doi.org</Authority>
                             </AssociatedDOI>
                           </AssociatedDOIs>"))

      "Test nominal Not Applicable collection DOI"
      {:MissingReason "Not Applicable",
       :Explanation (str "The collection is a near real time record "
                         "and does not have a DOI.")}
      [{:DOI "10.5678/assoc-doi-1", :Title "Title1", :Authority "doi.org"}
       {:DOI "10.5678/assoc-doi-2", :Title "Title2", :Authority "doi.org"}]
      (-> base-record
          (string/replace #"<DOI>10.5067/IAGYM8Q26QRE</DOI>"
                          "<MissingReason>Not Applicable</MissingReason>")
          (string/replace #"<Authority>https://doi.org/</Authority>\s+</DOI>"
                          (str "<Explanation>The collection is a near real time record and does not "
                               "have a DOI.</Explanation>"
                               "</DOI>
                                <AssociatedDOIs>
                                  <AssociatedDOI>
                                    <DOI>10.5678/assoc-doi-1</DOI>
                                    <Title>Title1</Title>
                                    <Authority>doi.org</Authority>
                                  </AssociatedDOI>
                                  <AssociatedDOI>
                                    <DOI>10.5678/assoc-doi-2</DOI>
                                    <Title>Title2</Title>
                                    <Authority>doi.org</Authority>
                                  </AssociatedDOI>
                                </AssociatedDOIs>")))

      "Test missing Collection DOI."
      {:MissingReason "Unknown",
       :Explanation "It is unknown if this record has a DOI."}
      [{:DOI "10.5678/assoc-doi-1", :Title "Title1", :Authority "doi.org"}
       {:DOI "10.5678/assoc-doi-2", :Title "Title2", :Authority "doi.org"}]
      (-> base-record
          (string/replace #"<DOI>\s+<DOI>10.5067/IAGYM8Q26QRE</DOI>"
                          "")
          (string/replace #"<Authority>https://doi.org/</Authority>\s+</DOI>"
                          "<AssociatedDOIs>
                            <AssociatedDOI>
                              <DOI>10.5678/assoc-doi-1</DOI>
                              <Title>Title1</Title>
                              <Authority>doi.org</Authority>
                            </AssociatedDOI>
                            <AssociatedDOI>
                              <DOI>10.5678/assoc-doi-2</DOI>
                              <Title>Title2</Title>
                              <Authority>doi.org</Authority>
                            </AssociatedDOI>
                          </AssociatedDOIs>"))

      "Test missing AssociatedDOIs."
      {:DOI "10.5067/IAGYM8Q26QRE"
       :Authority "https://doi.org/"}
      nil
      base-record

      "Test missing both Collection DOI and AssociatedDOIs."
      {:MissingReason "Unknown",
       :Explanation "It is unknown if this record has a DOI."}
      nil
      (-> base-record
          (string/replace #"<DOI>\s+<DOI>10.5067/IAGYM8Q26QRE</DOI>"
                          "")
          (string/replace #"<Authority>https://doi.org/</Authority>\s+</DOI>"
                          "")))))

(deftest echo10-use-constraints-test
  "Testing the echo10 use constraint translation from echo10 to umm-c."
  (let [base-record (slurp (io/resource "example-data/echo10/artificial_test_data.xml"))]

    (testing "echo10 use constraints description test"
      (let [actual-data (string/replace base-record
                                        #"</RestrictionFlag>"
                                        "</RestrictionFlag>
                                         <UseConstraints>
                                           <Description>Description</Description>
                                         </UseConstraints>")
            result (#'echo10/parse-echo10-xml (lkt/setup-context-for-test) actual-data true)
            umm-result (:UseConstraints result)]
        (is (= "Description" (:Description umm-result)))
        (is (= nil (:FreeAndOpenData umm-result)))))

    (testing "echo10 use constraints FreeAndOpenData test true"
      (let [actual-data (string/replace base-record
                                        #"</RestrictionFlag>"
                                        "</RestrictionFlag>
                                         <UseConstraints>
                                           <FreeAndOpenData>true</FreeAndOpenData>
                                           <LicenseURL>
                                             <URL>https://someurl.com</URL>
                                             <Description>License URL Description</Description>
                                             <Type>License URL</Type>
                                             <MimeType>text/html</MimeType>
                                           </LicenseURL>
                                         </UseConstraints>")
            result (#'echo10/parse-echo10-xml (lkt/setup-context-for-test) actual-data true)
            umm-result (:UseConstraints result)]
        (is (= true (:FreeAndOpenData umm-result)))))

    (testing "echo10 use constraints FreeAndOpenData test false"
      (let [actual-data (string/replace base-record
                                        #"</RestrictionFlag>"
                                        "</RestrictionFlag>
                                         <UseConstraints>
                                           <FreeAndOpenData>false</FreeAndOpenData>
                                           <LicenseURL>
                                             <URL>https://someurl.com</URL>
                                             <Description>License URL Description</Description>
                                             <Type>License URL</Type>
                                             <MimeType>text/html</MimeType>
                                           </LicenseURL>
                                         </UseConstraints>")
            result (#'echo10/parse-echo10-xml (lkt/setup-context-for-test) actual-data true)
            umm-result (:UseConstraints result)]
        (is (= false (:FreeAndOpenData umm-result)))))

    (testing "echo10 use constraints LicenseURL test"
      (let [actual-data (string/replace base-record
                                        #"</RestrictionFlag>"
                                        "</RestrictionFlag>
                                         <UseConstraints>
                                           <LicenseURL>
                                             <URL>https://someurl.com</URL>
                                             <Description>License URL Description</Description>
                                             <Type>License URL</Type>
                                             <MimeType>text/html</MimeType>
                                           </LicenseURL>
                                         </UseConstraints>")
            result (#'echo10/parse-echo10-xml (lkt/setup-context-for-test) actual-data true)
            umm-result (:UseConstraints result)]
        (is (and (= "https://someurl.com" (get-in umm-result [:LicenseURL :Linkage]))
                 (= "License URL Description" (get-in umm-result [:LicenseURL :Description]))
                 (= "License URL" (get-in umm-result [:LicenseURL :Name]))
                 (= "text/html" (get-in umm-result [:LicenseURL :MimeType]))))))

    (testing "echo10 use constraints License Text test"
      (let [actual-data (string/replace base-record
                                        #"</RestrictionFlag>"
                                        "</RestrictionFlag>
                                         <UseConstraints>
                                           <LicenseText>License Text</LicenseText>
                                         </UseConstraints>")
            result (#'echo10/parse-echo10-xml (lkt/setup-context-for-test) actual-data true)
            umm-result (:UseConstraints result)]
        (is (= "License Text" (:LicenseText umm-result)))))

    (testing "echo10 use constraints nil test"
      (let [result (#'echo10/parse-echo10-xml (lkt/setup-context-for-test) base-record true)
            umm-result (:UseConstraints result)]
        (is (= nil umm-result))))

    (testing "echo10 use constraints Description and LicenseURL test"
      (let [actual-data (string/replace base-record
                                        #"</RestrictionFlag>"
                                        "</RestrictionFlag>
                                         <UseConstraints>
                                           <Description>Description</Description>
                                           <LicenseURL>
                                             <URL>https://someurl.com</URL>
                                             <Description>License URL Description</Description>
                                             <Type>License URL</Type>
                                             <MimeType>text/html</MimeType>
                                           </LicenseURL>
                                         </UseConstraints>")
            result (#'echo10/parse-echo10-xml (lkt/setup-context-for-test) actual-data true)
            umm-result (:UseConstraints result)]
        (is (and (= "Description" (:Description umm-result))
                 (= "https://someurl.com" (get-in umm-result [:LicenseURL :Linkage]))
                 (= "License URL Description" (get-in umm-result [:LicenseURL :Description]))
                 (= "License URL" (get-in umm-result [:LicenseURL :Name]))
                 (= "text/html" (get-in umm-result [:LicenseURL :MimeType]))))))

    (testing "echo10 use constraints Description and License Text test"
      (let [actual-data (string/replace base-record
                                        #"</RestrictionFlag>"
                                        "</RestrictionFlag>
                                         <UseConstraints>
                                           <Description>Description</Description>
                                           <LicenseText>License Text</LicenseText>
                                         </UseConstraints>")
            result (#'echo10/parse-echo10-xml (lkt/setup-context-for-test) actual-data true)
            umm-result (:UseConstraints result)]
        (is (and (= "Description" (:Description umm-result))
                 (= "License Text" (:LicenseText umm-result))))))))
