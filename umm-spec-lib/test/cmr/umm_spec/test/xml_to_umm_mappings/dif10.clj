(ns cmr.umm-spec.test.xml-to-umm-mappings.dif10
  (:require [clj-time.core :as t]
            [clojure.test :refer :all]
            [cmr.common.util :as common-util :refer [are3]]
            [cmr.umm-spec.xml-to-umm-mappings.dif10 :as parse]))

(def options {:sanitize? true})

(deftest dif10-standard-product-test
  ;; Note: At this unit test level, all the values returned are strings.
  ;; Eventually, parse-umm-c in cmr.umm-spec.json-schema converts them to proper boolean values.
  ;; This test only verifies the last StandardProduct is chosen if more than one are present.
  (testing "multiple standard product in one Extended_Metadata"
    (is (= "true"
           (parse/parse-standard-product "<DIF>
                                           <Extended_Metadata>
                                             <Metadata>
                                               <Group>gov.nasa.gsfc.gcmd.standardproduct</Group>
                                               <Name>StandardProduct</Name>
                                               <Value>false</Value>
                                             </Metadata>
                                             <Metadata>
                                               <Group>gov.nasa.gsfc.gcmd.standardproduct</Group>
                                               <Name>StandardProduct</Name>
                                               <Value>true</Value>
                                             </Metadata>
                                           </Extended_Metadata>
                                         </DIF>"))))

  (testing "multiple standard product in multiple Extended_Metadata"
    (is (= "false"
           (parse/parse-standard-product "<DIF>
                                           <Extended_Metadata>
                                             <Metadata>
                                               <Group>gov.nasa.gsfc.gcmd.standardproduct</Group>
                                               <Name>StandardProduct</Name>
                                               <Value>true</Value>
                                             </Metadata>
                                           </Extended_Metadata>
                                           <Extended_Metadata>
                                             <Metadata>
                                               <Group>gov.nasa.gsfc.gcmd.standardproduct</Group>
                                               <Name>StandardProduct</Name>
                                               <Value>false</Value>
                                             </Metadata>
                                           </Extended_Metadata>
                                         </DIF>"))))

  (testing "multiple standard product in a mix of one and multiple Extended_Metadata"
    (is (= nil
           (parse/parse-standard-product "<DIF>
                                           <Extended_Metadata>
                                             <Metadata>
                                               <Group>gov.nasa.gsfc.gcmd.standardproduct</Group>
                                               <Name>StandardProduct</Name>
                                               <Value>true</Value>
                                             </Metadata>
                                             <Metadata>
                                               <Group>gov.nasa.gsfc.gcmd.standardproduct</Group>
                                               <Name>StandardProduct</Name>
                                               <Value>true</Value>
                                             </Metadata>
                                           </Extended_Metadata>
                                           <Extended_Metadata>
                                             <Metadata>
                                               <Group>gov.nasa.gsfc.gcmd.standardproduct</Group>
                                               <Name>StandardProduct</Name>
                                               <Value>true</Value>
                                             </Metadata>
                                           </Extended_Metadata>
                                           <Extended_Metadata>
                                             <Metadata>
                                               <Group>gov.nasa.gsfc.gcmd.standardproduct</Group>
                                               <Name>StandardProduct</Name>
                                               <Value></Value>
                                             </Metadata>
                                           </Extended_Metadata>
                                         </DIF>")))))

(deftest dif10-metadata-dates-test

  (testing "date elements with non-date values are skipped"
    (is (= [{:Type "UPDATE" :Date (t/date-time 2015 1 1)}]
           (parse/parse-data-dates "<DIF>
                                      <Metadata_Dates>
                                        <Data_Creation>obsequious lettuces</Data_Creation>
                                        <Data_Last_Revision>2015-01-01</Data_Last_Revision>
                                      </Metadata_Dates>
                                    </DIF>"))))

  (testing "valid dates return DataDates records"
    (is (= [{:Type "CREATE",
             :Date (t/date-time 2014 5 1 2 30 24)}]
           (parse/parse-data-dates "<DIF>
                                      <Metadata_Dates>
                                        <Data_Creation>2014-05-01T02:30:24</Data_Creation>
                                      </Metadata_Dates>
                                    </DIF>"))))
  
  (testing "all four metadata dates types are translated"
   (let [result (parse/parse-dif10-xml "<DIF><Metadata_Dates>
                                   <Metadata_Creation>2013-03-28</Metadata_Creation>
                                   <Metadata_Last_Revision>2016-05-11</Metadata_Last_Revision>
                                   <Metadata_Delete>2017-04-04</Metadata_Delete>
                                   <Metadata_Future_Review>2016-12-01</Metadata_Future_Review>
                                   </Metadata_Dates></DIF>" options)
         md-dates (:MetadataDates result)] 
     (is (= (list ["CREATE" (t/date-time 2013 3 28)] 
                  ["UPDATE" (t/date-time 2016 5 11)] 
                  ["DELETE" (t/date-time 2017 4 4)] 
                  ["REVIEW" (t/date-time 2016 12 1)])
         (map #(vector (:Type %) (:Date %)) md-dates)))))

  (testing "default date is skipped"
    (is (= []
           (parse/parse-data-dates "<DIF>
                                      <Metadata_Dates>
                                        <Data_Creation>1970-01-01T00:00:00</Data_Creation>
                                      </Metadata_Dates>
                                    </DIF>")))))

(deftest dif10-temporal-end-dates
  (is (= (t/date-time 2015 1 1 23 59 59 999)
         (-> (parse/parse-dif10-xml "<DIF>
                                       <Temporal_Coverage>
                                         <Range_DateTime>
                                           <Beginning_Date_Time>2014-01-01</Beginning_Date_Time>
                                           <Ending_Date_Time>2015-01-01</Ending_Date_Time>
                                         </Range_DateTime>
                                       </Temporal_Coverage>
                                     </DIF>"
                                     options)
             :TemporalExtents
             first
             :RangeDateTimes
             first
             :EndingDateTime)))
  (is (= (t/date-time 2015 1 1 4 30 12)
         (-> (parse/parse-dif10-xml "<DIF>
                                       <Temporal_Coverage>
                                         <Range_DateTime>
                                           <Beginning_Date_Time>2014-01-01T04:30:12</Beginning_Date_Time>
                                           <Ending_Date_Time>2015-01-01T04:30:12</Ending_Date_Time>
                                         </Range_DateTime>
                                       </Temporal_Coverage>
                                     </DIF>"
                                     options)
             :TemporalExtents
             first
             :RangeDateTimes
             first
             :EndingDateTime))))

(deftest dif10-direct-distribution-information-test
  "Testing the direct distribution information translation from dif10 to UMM-C."
  (is (= {:Region "us-west-1"
          :S3BucketAndObjectPrefixNames ["bucket1" "bucket2"]
          :S3CredentialsAPIEndpoint "https://www.credAPIURL.org"
          :S3CredentialsAPIDocumentationURL "https://www.credAPIDocURL.org"}
         (:DirectDistributionInformation
           (parse/parse-dif10-xml "<DIF>
                                     <DirectDistributionInformation>
                                       <Region>us-west-1</Region>
                                       <S3BucketAndObjectPrefixName>bucket1</S3BucketAndObjectPrefixName>
                                       <S3BucketAndObjectPrefixName>bucket2</S3BucketAndObjectPrefixName>
                                       <S3CredentialsAPIEndpoint>https://www.credAPIURL.org</S3CredentialsAPIEndpoint>
                                       <S3CredentialsAPIDocumentationURL>https://www.credAPIDocURL.org</S3CredentialsAPIDocumentationURL>
                                     </DirectDistributionInformation>
                                   </DIF>"
                                   options)))))

(deftest dif10-direct-distribution-information-nil-test
  "Testing the direct distribution information translation from dif10 to UMM-C when its nil."
  (is (= nil
         (:DirectDistributionInformation
           (parse/parse-dif10-xml "<DIF>
                                   </DIF>"
                                   options)))))

(deftest dif10-doi-translation-test
  "This tests the DIF 10 DOI translation from dif10 to UMM-C."

  (are3 [expected-doi-result expected-associated-doi-result test-string]
    (let [result (parse/parse-dif10-xml test-string options)]
      (is (= expected-doi-result
             (:DOI result))
          (= expected-associated-doi-result
             (:AssociatedDOIs result))))

    "Test the nominal success case."
    {:DOI "10.5067/IAGYM8Q26QRE"}
    [{:DOI "10.5678/assoc-doi-1"
      :Title "Title1"
      :Authority "doi.org"}
     {:DOI "10.5678/assoc-doi-2"
      :Title "Title2"
      :Authority "doi.org"}]
    "<DIF>
        <Dataset_Citation>
            <Dataset_Creator>JAXA</Dataset_Creator>
            <!--Dataset_Title was trimmed-->
            <Dataset_Title>Level 3 Soil moisture (AMSR2) product</Dataset_Title>
            <Persistent_Identifier>
              <Type>DOI</Type>
              <Identifier>10.5067/IAGYM8Q26QRE</Identifier>
            </Persistent_Identifier>
        </Dataset_Citation>
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
        </AssociatedDOIs>
     </DIF>"

    "Test missing Collection DOI."
    {:MissingReason "Unknown",
     :Explanation "It is unknown if this record has a DOI."}
    [{:DOI "10.5678/assoc-doi-1", :Title "Title1", :Authority "doi.org"}
     {:DOI "10.5678/assoc-doi-2", :Title "Title2", :Authority "doi.org"}]
    "<DIF>
        <Dataset_Citation>
            <Dataset_Creator>JAXA</Dataset_Creator>
            <!--Dataset_Title was trimmed-->
            <Dataset_Title>Level 3 Soil moisture (AMSR2) product</Dataset_Title>
        </Dataset_Citation>
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
        </AssociatedDOIs>
     </DIF>"

    "Test missing AssociatedDOIs."
    {:DOI "10.5067/IAGYM8Q26QRE"}
    nil
    "<DIF>
        <Dataset_Citation>
            <Dataset_Creator>JAXA</Dataset_Creator>
            <!--Dataset_Title was trimmed-->
            <Dataset_Title>Level 3 Soil moisture (AMSR2) product</Dataset_Title>
            <Persistent_Identifier>
              <Type>DOI</Type>
              <Identifier>10.5067/IAGYM8Q26QRE</Identifier>
            </Persistent_Identifier>
        </Dataset_Citation>
     </DIF>"

    "Test missing both Collection DOI and AssociatedDOIs."
    {:MissingReason "Unknown",
     :Explanation "It is unknown if this record has a DOI."}
    nil
    "<DIF>
     </DIF>"))

(deftest dif10-use-constraints-test
  "Testing the dif10 use constraint translation from dif10 to umm-c."

  (testing "dif10 use constraints description test"
    (let [actual-data "<DIF>
                         <Use_Constraints>
                           <Description>Description</Description>
                         </Use_Constraints>
                       </DIF>"
          result (parse/parse-dif10-xml actual-data options)
          umm-result (:UseConstraints result)]
      (is (= "Description" (:Description umm-result)))
      (is (= nil (:FreeAndOpenData umm-result)))))

  (testing "dif10 use constraints string test"
    (let [actual-data "<DIF><Use_Constraints>Description</Use_Constraints></DIF>"
          result (parse/parse-dif10-xml actual-data options)
          umm-result (:UseConstraints result)]
      (is (= "Description" (:Description umm-result)))))

  (testing "dif10 use constraints Free_And_Open_Data test true"
    (let [actual-data "<DIF>
                         <Use_Constraints>
                           <Description>Description</Description>
                           <Free_And_Open_Data>true</Free_And_Open_Data>
                         </Use_Constraints>
                       </DIF>"
          result (parse/parse-dif10-xml actual-data options)
          umm-result (:UseConstraints result)]
      (is (true? (:FreeAndOpenData umm-result)))))

  (testing "dif10 use constraints Free_And_Open_Data test false"
    (let [actual-data "<DIF>
                         <Use_Constraints>
                           <Description>Description</Description>
                           <Free_And_Open_Data>false</Free_And_Open_Data>
                         </Use_Constraints>
                       </DIF>"
          result (parse/parse-dif10-xml actual-data options)
          umm-result (:UseConstraints result)]
      (is (false? (:FreeAndOpenData umm-result)))))

  (testing "dif10 use constraints LicenseURL test"
    (let [actual-data "<DIF>
                         <Use_Constraints>
                           <License_URL>
                             <URL>https://someurl.com</URL>
                             <Title>License URL</Title>
                             <Description>License URL Description</Description>
                             <Mime_Type>text/html</Mime_Type>
                           </License_URL>
                         </Use_Constraints>
                       </DIF>"
          result (parse/parse-dif10-xml actual-data options)
          umm-result (:UseConstraints result)]
      (is (= "https://someurl.com" (get-in umm-result [:LicenseURL :Linkage])))
      (is (= "License URL Description" (get-in umm-result [:LicenseURL :Description])))
      (is (= "License URL" (get-in umm-result [:LicenseURL :Name])))
      (is (= "text/html" (get-in umm-result [:LicenseURL :MimeType])))))

  (testing "dif10 use constraints License Text test"
    (let [actual-data "<DIF>
                         <Use_Constraints>
                           <License_Text>License Text</License_Text>
                         </Use_Constraints>
                       </DIF>"
          result (parse/parse-dif10-xml actual-data options)
          umm-result (:UseConstraints result)]
      (is (= "License Text" (:LicenseText umm-result)))))

  (testing "dif10 use constraints nil test"
    (let [result (parse/parse-dif10-xml "<DIF></DIF>" options)
          umm-result (:UseConstraints result)]
      (is (= nil umm-result))))

  (testing "dif10 use constraints is emtpy tag"
    (let [result (parse/parse-dif10-xml "<DIF><Use_Constraints/></DIF>" options)
          umm-result (:UseConstraints result)]
      (is (= nil umm-result))))

  (testing "dif10 use constraints Description and LicenseURL test"
    (let [actual-data "<DIF>
                         <Use_Constraints>
                           <Description>Description</Description>
                           <License_URL>
                             <URL>https://someurl.com</URL>
                             <Title>License URL</Title>
                             <Description>License URL Description</Description>
                             <Mime_Type>text/html</Mime_Type>
                           </License_URL>
                         </Use_Constraints>
                       </DIF>"
          result (parse/parse-dif10-xml actual-data options)
          umm-result (:UseConstraints result)]
      (is (= "Description" (:Description umm-result)))
      (is (= "https://someurl.com" (get-in umm-result [:LicenseURL :Linkage])))
      (is (= "License URL Description" (get-in umm-result [:LicenseURL :Description])))
      (is (= "License URL" (get-in umm-result [:LicenseURL :Name])))
      (is (= "text/html" (get-in umm-result [:LicenseURL :MimeType])))))

  (testing "dif10 use constraints Description and License Text test"
    (let [actual-data "<DIF>
                         <Use_Constraints>
                           <Description>Description</Description>
                           <License_Text>License Text</License_Text>
                         </Use_Constraints>
                       </DIF>"
          result (parse/parse-dif10-xml actual-data options)
          umm-result (:UseConstraints result)]
      (is (= "Description" (:Description umm-result)))
      (is (= "License Text" (:LicenseText umm-result))))))

(deftest dif10-related-urls-get-capabilities-test
  "This tests the DIF 10 related-url GET CAPABILITIES XML to UMM-C translation"

  (are3 [expected record]
    (let [result (parse/parse-dif10-xml record options)]
      (is (= expected (:RelatedUrls result))))

    "Parsing USE SERVICE API into GET CAPABILITIES"
    [{:URL "http://someurl.com/"
      :Description "Some description"
      :URLContentType "DistributionURL"
      :Type "GET CAPABILITIES"
      :Subtype "OpenSearch"
      :GetData {:Format "Not provided"
                :Size 0.0,
                :Unit "KB",
                :MimeType "application/opensearchdescription+xml"}}]
    "<DIF>
       <Related_URL>
         <URL_Content_Type>
           <Type>USE SERVICE API</Type>
           <Subtype>OpenSearch</Subtype>
         </URL_Content_Type>
         <URL>http://someurl.com/</URL>
         <Description>Some description</Description>
         <Mime_Type>application/opensearchdescription+xml</Mime_Type>
       </Related_URL>
     </DIF>"

    "Checking USE SERVICE API and mime-type does not exist."
    [{:URL "http://someurl.com/"
      :Description "Some description"
      :URLContentType "DistributionURL"
      :Type "USE SERVICE API"
      :Subtype "OpenSearch"
      :GetService nil}]
    "<DIF>
       <Related_URL>
         <URL_Content_Type>
           <Type>USE SERVICE API</Type>
           <Subtype>OpenSearch</Subtype>
         </URL_Content_Type>
         <URL>http://someurl.com/</URL>
         <Description>Some description</Description>
       </Related_URL>
     </DIF>"))
