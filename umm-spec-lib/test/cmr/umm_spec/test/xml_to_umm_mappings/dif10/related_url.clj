(ns cmr.umm-spec.test.xml-to-umm-mappings.dif10.related-url
  (:require [clj-time.core :as t]
            [clojure.test :refer :all]
            [cmr.common.util :as common-util :refer [are3]]
            [cmr.umm-spec.xml-to-umm-mappings.dif10.related-url :as ru]))

(deftest dif10-metadata-related-url-test
  (testing "parse related urls from Multimedia_Sample"
    (is (= [{:URL "http://disc.sci.gsfc.nasa.gov/OCO-2/images/ACOS.xCO2.2013.v3.5.png"
             :Description " Global amounts of column CO2 in 2013, ..."
             :URLContentType "VisualizationURL"
             :Type "GET RELATED VISUALIZATION"}]
           (ru/parse-related-urls
             "<DIF>
                <Multimedia_Sample>
                  <URL>http://disc.sci.gsfc.nasa.gov/OCO-2/images/ACOS.xCO2.2013.v3.5.png</URL>
                  <Format>PNG</Format>
                  <Caption>ACOS xCO2 v2.5, yearly mean for 2013, in part per million in volume</Caption>
                  <Description> Global amounts of column CO2 in 2013, ...</Description>
                </Multimedia_Sample>
              </DIF>"
             true))))

  (testing "parse realted urls from Related_URL and Multimedia_Sample together"
    (is (= [{:URL "http://disc.sci.gsfc.nasa.gov/OCO-2/images/ACOS.xCO2.2013.v3.5.png"
             :Description " Global amounts of column CO2 in 2013, ..."
             :URLContentType "VisualizationURL"
             :Type "GET RELATED VISUALIZATION"}
            {:URL "http://reverb.echo.nasa.gov/reverb/"
             :Description "Interface to search, discover, and access EOS data products, and invoke available data services."
             :URLContentType "DistributionURL"
             :Type "GET DATA"
             :Subtype "Earthdata Search"
             :GetData nil}]
           (ru/parse-related-urls
             "<DIF>
                <Multimedia_Sample>
                  <URL>http://disc.sci.gsfc.nasa.gov/OCO-2/images/ACOS.xCO2.2013.v3.5.png</URL>
                  <Format>PNG</Format>
                  <Caption>ACOS xCO2 v2.5, yearly mean for 2013, in part per million in volume</Caption>
                  <Description> Global amounts of column CO2 in 2013, ...</Description>
                </Multimedia_Sample>
                <Related_URL>
                  <URL_Content_Type>
                    <Type>GET DATA</Type>
                    <Subtype>REVERB</Subtype>
                  </URL_Content_Type>
                  <URL>http://reverb.echo.nasa.gov/reverb/</URL>
                  <Description>Interface to search, discover, and access EOS data products, and invoke available data services.</Description>
                </Related_URL>
              </DIF>"
              true)))))

(deftest dif10-related-urls-get-capabilities-test
  "This tests the DIF 10 related-url GET CAPABILITIES XML to UMM-C translation"

  (are3 [expected record]
    (let [result (ru/parse-related-urls record true)]
      (is (= expected result)))

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

    "Checking USE SERVICE API and mime-type is not the right value."
    [{:URL "http://someurl.com/"
      :Description "Some description"
      :URLContentType "DistributionURL"
      :Type "USE SERVICE API"
      :Subtype "OpenSearch"
      :GetService
        {:MimeType "opensearchdescription+xml",
         :FullName "Not provided",
         :Format "Not provided",
         :DataID "Not provided",
         :DataType "Not provided",
         :Protocol "HTTP"}}]
    "<DIF>
       <Related_URL>
         <URL_Content_Type>
           <Type>USE SERVICE API</Type>
           <Subtype>OpenSearch</Subtype>
         </URL_Content_Type>
         <URL>http://someurl.com/</URL>
         <Description>Some description</Description>
         <Mime_Type>opensearchdescription+xml</Mime_Type>
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
     </DIF>"

    "Checking USE SERVICE API and SubType is not the right value."
    [{:URL "http://someurl.com/"
      :Description "Some description"
      :URLContentType "DistributionURL"
      :Type "USE SERVICE API"
      :Subtype "OPENDAP DATA"
      :GetService
        {:MimeType "application/opensearchdescription+xml",
         :FullName "Not provided",
         :Format "Not provided",
         :DataID "Not provided",
         :DataType "Not provided",
         :Protocol "HTTP"}}]
    "<DIF>
       <Related_URL>
         <URL_Content_Type>
           <Type>USE SERVICE API</Type>
           <Subtype>OPENDAP DATA</Subtype>
         </URL_Content_Type>
         <URL>http://someurl.com/</URL>
         <Description>Some description</Description>
         <Mime_Type>application/opensearchdescription+xml</Mime_Type>
       </Related_URL>
     </DIF>"

    "Checking USE SERVICE API and SubType doesn't exist."
    [{:URL "http://someurl.com/"
      :Description "Some description"
      :URLContentType "DistributionURL"
      :Type "USE SERVICE API"
      :GetService
        {:MimeType "application/opensearchdescription+xml",
         :FullName "Not provided",
         :Format "Not provided",
         :DataID "Not provided",
         :DataType "Not provided",
         :Protocol "HTTP"}}]
    "<DIF>
       <Related_URL>
         <URL_Content_Type>
           <Type>USE SERVICE API</Type>
         </URL_Content_Type>
         <URL>http://someurl.com/</URL>
         <Description>Some description</Description>
         <Mime_Type>application/opensearchdescription+xml</Mime_Type>
       </Related_URL>
     </DIF>"

    "Checking GET CAPABILITIES."
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
           <Type>GET CAPABILITIES</Type>
           <Subtype>OpenSearch</Subtype>
         </URL_Content_Type>
         <URL>http://someurl.com/</URL>
         <Description>Some description</Description>
         <Mime_Type>application/opensearchdescription+xml</Mime_Type>
       </Related_URL>
     </DIF>"

    "Checking GET CAPABILITIES without Subtype."
    [{:URL "http://someurl.com/"
      :Description "Some description"
      :URLContentType "DistributionURL"
      :Type "GET CAPABILITIES"
      :GetData {:Format "Not provided"
                :Size 0.0,
                :Unit "KB",
                :MimeType "application/opensearchdescription+xml"}}]
    "<DIF>
       <Related_URL>
         <URL_Content_Type>
           <Type>GET CAPABILITIES</Type>
         </URL_Content_Type>
         <URL>http://someurl.com/</URL>
         <Description>Some description</Description>
         <Mime_Type>application/opensearchdescription+xml</Mime_Type>
       </Related_URL>
     </DIF>"

    "Checking GET CAPABILITIES without mime type."
    [{:URL "http://someurl.com/"
      :Description "Some description"
      :URLContentType "DistributionURL"
      :Type "GET CAPABILITIES"
      :Subtype "OpenSearch"
      :GetData nil}]
    "<DIF>
       <Related_URL>
         <URL_Content_Type>
           <Type>GET CAPABILITIES</Type>
           <Subtype>OpenSearch</Subtype>
         </URL_Content_Type>
         <URL>http://someurl.com/</URL>
         <Description>Some description</Description>
       </Related_URL>
     </DIF>"

    "Checking GET CAPABILITIES without subtype and mime type."
    [{:URL "http://someurl.com/"
      :Description "Some description"
      :URLContentType "DistributionURL"
      :Type "GET CAPABILITIES"
      :GetData nil}]
    "<DIF>
       <Related_URL>
         <URL_Content_Type>
           <Type>GET CAPABILITIES</Type>
         </URL_Content_Type>
         <URL>http://someurl.com/</URL>
         <Description>Some description</Description>
       </Related_URL>
     </DIF>"))
