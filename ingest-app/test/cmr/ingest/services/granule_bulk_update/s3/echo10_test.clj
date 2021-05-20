(ns cmr.ingest.services.granule-bulk-update.s3.echo10-test
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.ingest.services.granule-bulk-update.s3.echo10 :as echo10]
   [cmr.ingest.services.granule-bulk-update.s3.s3-util :as s3-util]))

(def ^:private add-at-the-end-gran-xml
  "ECHO10 granule for testing adding S3 url at the end of the xml."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
  </Granule>")

(def ^:private add-at-the-end-gran-xml-result
  "Result ECHO10 granule after adding S3 url at the end of the xml.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineAccessURLs>
      <OnlineAccessURL>
         <URL>s3://abcd/foo</URL>
         <URLDescription>This link provides direct download access via S3 to the granule.</URLDescription>
      </OnlineAccessURL>
   </OnlineAccessURLs>
</Granule>\n")

(def ^:private add-before-element-gran-xml
  "ECHO10 granule for testing adding S3 url at before an element that comes after
   the OnlineAccessURLs in the xml."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <OnlineResources>
      <OnlineResource>
        <URL>http://opendap-url.example.com</URL>
        <Type>GET DATA : OPENDAP DATA</Type>
      </OnlineResource>
    </OnlineResources>
  </Granule>")

(def ^:private add-before-element-gran-xml-result
  "Result ECHO10 granule after adding S3 url before an element in the xml.
  Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineAccessURLs>
      <OnlineAccessURL>
         <URL>s3://abcd/foo</URL>
         <URLDescription>This link provides direct download access via S3 to the granule.</URLDescription>
      </OnlineAccessURL>
   </OnlineAccessURLs>
   <OnlineResources>
      <OnlineResource>
         <URL>http://opendap-url.example.com</URL>
         <Type>GET DATA : OPENDAP DATA</Type>
      </OnlineResource>
   </OnlineResources>
</Granule>\n")

(def ^:private add-with-empty-gran-xml
  "ECHO10 granule for testing adding S3 url with an empty OnlineAccessURLs element in the xml."
  "<Granule>
     <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
     <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
     <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
     <Collection>
        <EntryId>AQUARIUS_L1A_SSS</EntryId>
     </Collection>
     <OnlineAccessURLs/>
     <Orderable>false</Orderable>
  </Granule>")

(def ^:private add-with-empty-gran-xml-result
  "Result ECHO10 granule after adding S3 url with empty OnlineAccessURL element in the xml.
  Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineAccessURLs>
      <OnlineAccessURL>
         <URL>s3://abcd/foo</URL>
         <URLDescription>This link provides direct download access via S3 to the granule.</URLDescription>
      </OnlineAccessURL>
   </OnlineAccessURLs>
   <Orderable>false</Orderable>
</Granule>\n")

(def ^:private add-with-no-match-gran-xml
  "ECHO10 granule for testing adding S3 url to OnlineAccessURLs that has no S3 url."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <OnlineAccessURLs>
       <OnlineAccessURL>
          <URL>http://example.com/doc</URL>
          <URLDescription>Files may be downloaded directly to your workstation from this link</URLDescription>
          <MimeType>text/html</MimeType>
       </OnlineAccessURL>
       <OnlineAccessURL>
          <URL>https://oceandata.sci.gsfc.nasa.gov/MODIS-Terra/L3BIN/</URL>
       			<URLDescription>OB.DAAC Data Distribution Website for MODIS-Terra L3B Sea Surface Temperature (SST) Product</URLDescription>
       			<MimeType>GET DATA</MimeType>
       </OnlineAccessURL>
    </OnlineAccessURLs>
    <Orderable>false</Orderable>
  </Granule>")

(def ^:private add-with-no-match-gran-xml-result
  "Result ECHO10 granule for testing adding S3 url to OnlineAccessURLs that has no S3 url.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineAccessURLs>
      <OnlineAccessURL>
         <URL>s3://abcd/foo</URL>
         <URLDescription>This link provides direct download access via S3 to the granule.</URLDescription>
      </OnlineAccessURL>
      <OnlineAccessURL>
         <URL>http://example.com/doc</URL>
         <URLDescription>Files may be downloaded directly to your workstation from this link</URLDescription>
         <MimeType>text/html</MimeType>
      </OnlineAccessURL>
      <OnlineAccessURL>
         <URL>https://oceandata.sci.gsfc.nasa.gov/MODIS-Terra/L3BIN/</URL>
         <URLDescription>OB.DAAC Data Distribution Website for MODIS-Terra L3B Sea Surface Temperature (SST) Product</URLDescription>
         <MimeType>GET DATA</MimeType>
      </OnlineAccessURL>
   </OnlineAccessURLs>
   <Orderable>false</Orderable>
</Granule>\n")

(def ^:private update-s3-url
  "ECHO10 granule for testing updating S3 url existing in OnlineAccessURLs."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <OnlineAccessURLs>
       <OnlineAccessURL>
          <URL>http://example.com/doc</URL>
          <URLDescription>Files may be downloaded directly to your workstation from this link</URLDescription>
          <MimeType>text/html</MimeType>
       </OnlineAccessURL>
       <OnlineAccessURL>
          <URL>s3://abcd/to_be_updated</URL>
          <URLDescription>Files may be downloaded directly to your workstation from this link</URLDescription>
          <MimeType>GET DATA</MimeType>
       </OnlineAccessURL>
       <OnlineAccessURL>
          <URL>https://oceandata.sci.gsfc.nasa.gov/MODIS-Terra/L3BIN/</URL>
          <URLDescription>OB.DAAC Data Distribution Website for MODIS-Terra L3B Sea Surface Temperature (SST) Product</URLDescription>
          <MimeType>GET DATA</MimeType>
       </OnlineAccessURL>
    </OnlineAccessURLs>
    <Orderable>false</Orderable>
  </Granule>")

(def ^:private update-s3-url-result
  "Result ECHO10 granule for testing updating S3 url existing in OnlineAccessURLs.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineAccessURLs>
      <OnlineAccessURL>
         <URL>s3://abcd/foo</URL>
         <URLDescription>This link provides direct download access via S3 to the granule.</URLDescription>
      </OnlineAccessURL>
      <OnlineAccessURL>
         <URL>http://example.com/doc</URL>
         <URLDescription>Files may be downloaded directly to your workstation from this link</URLDescription>
         <MimeType>text/html</MimeType>
      </OnlineAccessURL>
      <OnlineAccessURL>
         <URL>https://oceandata.sci.gsfc.nasa.gov/MODIS-Terra/L3BIN/</URL>
         <URLDescription>OB.DAAC Data Distribution Website for MODIS-Terra L3B Sea Surface Temperature (SST) Product</URLDescription>
         <MimeType>GET DATA</MimeType>
      </OnlineAccessURL>
   </OnlineAccessURLs>
   <Orderable>false</Orderable>
</Granule>\n")

(def ^:private appended-s3-url
  "ECHO10 granule for testing updating S3 url existing in OnlineAccessURLs."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <OnlineAccessURLs>
       <OnlineAccessURL>
          <URL>http://example.com/doc</URL>
          <URLDescription>Files may be downloaded directly to your workstation from this link</URLDescription>
          <MimeType>text/html</MimeType>
       </OnlineAccessURL>
       <OnlineAccessURL>
          <URL>s3://abcd/to_remain</URL>
          <URLDescription>Files may be downloaded directly to your workstation from this link</URLDescription>
          <MimeType>GET DATA</MimeType>
       </OnlineAccessURL>
       <OnlineAccessURL>
          <URL>https://oceandata.sci.gsfc.nasa.gov/MODIS-Terra/L3BIN/</URL>
          <URLDescription>OB.DAAC Data Distribution Website for MODIS-Terra L3B Sea Surface Temperature (SST) Product</URLDescription>
          <MimeType>GET DATA</MimeType>
       </OnlineAccessURL>
    </OnlineAccessURLs>
    <Orderable>false</Orderable>
  </Granule>")

(def ^:private appended-s3-url-result
  "Result ECHO10 granule for testing appending S3 url existing in OnlineAccessURLs.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineAccessURLs>
      <OnlineAccessURL>
         <URL>s3://abcd/foo</URL>
         <URLDescription>This link provides direct download access via S3 to the granule.</URLDescription>
      </OnlineAccessURL>
      <OnlineAccessURL>
         <URL>http://example.com/doc</URL>
         <URLDescription>Files may be downloaded directly to your workstation from this link</URLDescription>
         <MimeType>text/html</MimeType>
      </OnlineAccessURL>
      <OnlineAccessURL>
         <URL>s3://abcd/to_remain</URL>
         <URLDescription>Files may be downloaded directly to your workstation from this link</URLDescription>
         <MimeType>GET DATA</MimeType>
      </OnlineAccessURL>
      <OnlineAccessURL>
         <URL>https://oceandata.sci.gsfc.nasa.gov/MODIS-Terra/L3BIN/</URL>
         <URLDescription>OB.DAAC Data Distribution Website for MODIS-Terra L3B Sea Surface Temperature (SST) Product</URLDescription>
         <MimeType>GET DATA</MimeType>
      </OnlineAccessURL>
   </OnlineAccessURLs>
   <Orderable>false</Orderable>
</Granule>\n")

(def ^:private update-multiple-s3-url
  "ECHO10 granule for testing updating multiple S3 url existing in OnlineAccessURLs."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <OnlineAccessURLs>
       <OnlineAccessURL>
          <URL>http://example.com/doc</URL>
          <URLDescription>Files may be downloaded directly to your workstation from this link</URLDescription>
          <MimeType>text/html</MimeType>
       </OnlineAccessURL>
       <OnlineAccessURL>
          <URL>s3://abcd/to_be_updated</URL>
          <URLDescription>Files may be downloaded directly to your workstation from this link</URLDescription>
          <MimeType>GET DATA</MimeType>
       </OnlineAccessURL>
       <OnlineAccessURL>
          <URL>s3://abcd/to_be_updated2</URL>
          <MimeType>GET DATA</MimeType>
       </OnlineAccessURL>
       <OnlineAccessURL>
          <URL>https://oceandata.sci.gsfc.nasa.gov/MODIS-Terra/L3BIN/</URL>
          <URLDescription>OB.DAAC Data Distribution Website for MODIS-Terra L3B Sea Surface Temperature (SST) Product</URLDescription>
          <MimeType>GET DATA</MimeType>
       </OnlineAccessURL>
    </OnlineAccessURLs>
    <Orderable>false</Orderable>
  </Granule>")

(def ^:private update-with-multiple-s3-url-result
  "Result ECHO10 granule for testing updating with multiple S3 urls in the input.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineAccessURLs>
      <OnlineAccessURL>
         <URL>s3://abcd/foo</URL>
         <URLDescription>This link provides direct download access via S3 to the granule.</URLDescription>
      </OnlineAccessURL>
      <OnlineAccessURL>
         <URL>s3://abcd/bar</URL>
         <URLDescription>This link provides direct download access via S3 to the granule.</URLDescription>
      </OnlineAccessURL>
      <OnlineAccessURL>
         <URL>http://example.com/doc</URL>
         <URLDescription>Files may be downloaded directly to your workstation from this link</URLDescription>
         <MimeType>text/html</MimeType>
      </OnlineAccessURL>
      <OnlineAccessURL>
         <URL>https://oceandata.sci.gsfc.nasa.gov/MODIS-Terra/L3BIN/</URL>
         <URLDescription>OB.DAAC Data Distribution Website for MODIS-Terra L3B Sea Surface Temperature (SST) Product</URLDescription>
         <MimeType>GET DATA</MimeType>
      </OnlineAccessURL>
   </OnlineAccessURLs>
   <Orderable>false</Orderable>
</Granule>\n")

(def ^:private appended-multiple-s3-url
  "ECHO10 granule for testing updating multiple S3 url existing in OnlineAccessURLs."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <OnlineAccessURLs>
       <OnlineAccessURL>
          <URL>http://example.com/doc</URL>
          <URLDescription>Files may be downloaded directly to your workstation from this link</URLDescription>
          <MimeType>text/html</MimeType>
       </OnlineAccessURL>
       <OnlineAccessURL>
          <URL>https://oceandata.sci.gsfc.nasa.gov/MODIS-Terra/L3BIN/</URL>
          <URLDescription>OB.DAAC Data Distribution Website for MODIS-Terra L3B Sea Surface Temperature (SST) Product</URLDescription>
          <MimeType>GET DATA</MimeType>
       </OnlineAccessURL>
       <OnlineAccessURL>
          <URL>s3://abcd/to_remain</URL>
          <URLDescription>Files may be downloaded directly to your workstation from this link</URLDescription>
          <MimeType>GET DATA</MimeType>
       </OnlineAccessURL>
       <OnlineAccessURL>
          <URL>s3://abcd/to_remain2</URL>
          <MimeType>GET DATA</MimeType>
       </OnlineAccessURL>
    </OnlineAccessURLs>
    <Orderable>false</Orderable>
  </Granule>")

(def ^:private appended-multiple-s3-url-result
  "ECHO10 granule for testing updating multiple S3 url existing in OnlineAccessURLs."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineAccessURLs>
      <OnlineAccessURL>
         <URL>s3://abcd/foo</URL>
         <URLDescription>This link provides direct download access via S3 to the granule.</URLDescription>
      </OnlineAccessURL>
      <OnlineAccessURL>
         <URL>s3://abcd/bar</URL>
         <URLDescription>This link provides direct download access via S3 to the granule.</URLDescription>
      </OnlineAccessURL>
      <OnlineAccessURL>
         <URL>http://example.com/doc</URL>
         <URLDescription>Files may be downloaded directly to your workstation from this link</URLDescription>
         <MimeType>text/html</MimeType>
      </OnlineAccessURL>
      <OnlineAccessURL>
         <URL>https://oceandata.sci.gsfc.nasa.gov/MODIS-Terra/L3BIN/</URL>
         <URLDescription>OB.DAAC Data Distribution Website for MODIS-Terra L3B Sea Surface Temperature (SST) Product</URLDescription>
         <MimeType>GET DATA</MimeType>
      </OnlineAccessURL>
      <OnlineAccessURL>
         <URL>s3://abcd/to_remain</URL>
         <URLDescription>Files may be downloaded directly to your workstation from this link</URLDescription>
         <MimeType>GET DATA</MimeType>
      </OnlineAccessURL>
      <OnlineAccessURL>
         <URL>s3://abcd/to_remain2</URL>
         <MimeType>GET DATA</MimeType>
      </OnlineAccessURL>
   </OnlineAccessURLs>
   <Orderable>false</Orderable>
</Granule>
")

(def ^:private appended-updated-multiple-s3-url-result
  "ECHO10 granule for testing updating append with existing S3 url existing in OnlineAccessURLs."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineAccessURLs>
      <OnlineAccessURL>
         <URL>s3://abcd/bar</URL>
         <URLDescription>This link provides direct download access via S3 to the granule.</URLDescription>
      </OnlineAccessURL>
      <OnlineAccessURL>
         <URL>http://example.com/doc</URL>
         <URLDescription>Files may be downloaded directly to your workstation from this link</URLDescription>
         <MimeType>text/html</MimeType>
      </OnlineAccessURL>
      <OnlineAccessURL>
         <URL>https://oceandata.sci.gsfc.nasa.gov/MODIS-Terra/L3BIN/</URL>
         <URLDescription>OB.DAAC Data Distribution Website for MODIS-Terra L3B Sea Surface Temperature (SST) Product</URLDescription>
         <MimeType>GET DATA</MimeType>
      </OnlineAccessURL>
      <OnlineAccessURL>
         <URL>s3://abcd/to_remain</URL>
         <URLDescription>Files may be downloaded directly to your workstation from this link</URLDescription>
         <MimeType>GET DATA</MimeType>
      </OnlineAccessURL>
      <OnlineAccessURL>
         <URL>s3://abcd/to_remain2</URL>
         <MimeType>GET DATA</MimeType>
      </OnlineAccessURL>
   </OnlineAccessURLs>
   <Orderable>false</Orderable>
</Granule>
")

(deftest add-s3-url
  (testing "add OnlineAccessURLs at various places in the xml"
    (are3 [url-value source result]
      (let [urls (s3-util/validate-url url-value)]
        (is (= result
               (#'echo10/update-s3-url-metadata source urls :replace))))

      "add OnlineAccessURLs at the end of the xml"
      "s3://abcd/foo"
      add-at-the-end-gran-xml
      add-at-the-end-gran-xml-result

      "add OnlineAccessURLs before an element in the xml"
      "s3://abcd/foo"
      add-before-element-gran-xml
      add-before-element-gran-xml-result

      "add OnlineAccessURLs to empty OnlineAccessURLs in the xml"
      "s3://abcd/foo"
      add-with-empty-gran-xml
      add-with-empty-gran-xml-result

      "add OnlineAccessURLs to OnlineAccessURLs without S3 url in the xml"
      "s3://abcd/foo"
      add-with-no-match-gran-xml
      add-with-no-match-gran-xml-result

      "update OnlineAccessURLs when single S3 url is present in xml"
      "s3://abcd/foo"
      update-s3-url
      update-s3-url-result

      "Update OnlineAccessURLs when multiple S3 urls are present in xml"
      "s3://abcd/foo"
      update-multiple-s3-url
      update-s3-url-result
      
      "update OnlineAccessURLs with multiple S3 urls in input"
      "s3://abcd/foo,s3://abcd/bar"
      update-s3-url
      update-with-multiple-s3-url-result)))

(deftest append-s3-url
  (testing "append OnlineAccessURLs at various places in the xml"
    (are3 [url-value source result]
      (let [urls (s3-util/validate-url url-value)]
        (is (= result
               (#'echo10/update-s3-url-metadata source urls :append))))

      "append OnlineAccessURLs at the end of the xml"
      "s3://abcd/foo"
      add-at-the-end-gran-xml
      add-at-the-end-gran-xml-result

      "prepend OnlineAccessURLs before an element in the xml"
      "s3://abcd/foo"
      add-before-element-gran-xml
      add-before-element-gran-xml-result

      "append OnlineAccessURLs to empty OnlineAccessURLs in the xml"
      "s3://abcd/foo"
      add-with-empty-gran-xml
      add-with-empty-gran-xml-result

      "add OnlineAccessURLs to OnlineAccessURLs without S3 url in the xml"
      "s3://abcd/foo"
      add-with-no-match-gran-xml
      add-with-no-match-gran-xml-result

      "append OnlineAccessURLs when single S3 url is present in xml"
      "s3://abcd/foo"
      appended-s3-url
      appended-s3-url-result

      "update OnlineAccessURLs when multiple S3 urls are present in xml"
      "s3://abcd/foo,s3://abcd/bar"
      appended-multiple-s3-url 
      appended-multiple-s3-url-result
      
      "update OnlineAccessURLs with multiple S3 urls in input"
      "s3://abcd/to_remain,s3://abcd/bar"
      appended-multiple-s3-url
      appended-updated-multiple-s3-url-result)))
