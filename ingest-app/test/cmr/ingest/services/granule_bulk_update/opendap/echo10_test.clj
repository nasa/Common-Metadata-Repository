(ns cmr.ingest.services.granule-bulk-update.opendap.echo10-test
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.ingest.services.granule-bulk-update.opendap.echo10 :as echo10]
   [cmr.ingest.services.granule-bulk-update.opendap.opendap-util :as opendap-util]))

(def ^:private add-at-the-end-gran-xml
  "ECHO10 granule for testing adding OPeNDAP url at the end of the xml."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
  </Granule>")

(def ^:private add-at-the-end-gran-xml-result
  "Result ECHO10 granule after adding OPeNDAP url at the end of the xml.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineResources>
      <OnlineResource>
         <URL>http://example.com/foo</URL>
         <Type>USE SERVICE API : OPENDAP DATA</Type>
      </OnlineResource>
   </OnlineResources>
</Granule>\n")

(def ^:private add-before-element-gran-xml
  "ECHO10 granule for testing adding OPeNDAP url at before an element that comes after
   the OnlineResources in the xml."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <Orderable>false</Orderable>
  </Granule>")

(def ^:private add-before-element-gran-xml-result
  "Result ECHO10 granule after adding OPeNDAP url before an element in the xml.
  Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineResources>
      <OnlineResource>
         <URL>http://example.com/foo</URL>
         <Type>USE SERVICE API : OPENDAP DATA</Type>
      </OnlineResource>
   </OnlineResources>
   <Orderable>false</Orderable>
</Granule>\n")

(def ^:private add-with-empty-gran-xml
  "ECHO10 granule for testing adding OPeNDAP url with an empty OnlineResources element in the xml."
  "<Granule>
     <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
     <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
     <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
     <Collection>
        <EntryId>AQUARIUS_L1A_SSS</EntryId>
     </Collection>
     <OnlineResources/>
     <Orderable>false</Orderable>
  </Granule>")

(def ^:private add-with-no-match-gran-xml
  "ECHO10 granule for testing adding OPeNDAP url to OnlineResources that has no OPeNDAP url."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <OnlineResources>
       <OnlineResource>
          <URL>http://example.com/doc</URL>
          <Type>Documentation</Type>
       </OnlineResource>
       <OnlineResource>
          <URL>http://example.com/Browse</URL>
          <Type>Browse</Type>
       </OnlineResource>
    </OnlineResources>
    <Orderable>false</Orderable>
  </Granule>")

(def ^:private add-with-no-match-gran-xml-result
  "Result ECHO10 granule for testing adding OPeNDAP url to OnlineResources that has no OPeNDAP url.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineResources>
      <OnlineResource>
         <URL>http://example.com/foo</URL>
         <Type>USE SERVICE API : OPENDAP DATA</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>http://example.com/doc</URL>
         <Type>Documentation</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>http://example.com/Browse</URL>
         <Type>Browse</Type>
      </OnlineResource>
   </OnlineResources>
   <Orderable>false</Orderable>
</Granule>\n")

(def ^:private update-opendap-url
  "ECHO10 granule for testing updating OPeNDAP url existing in OnlineResources."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <OnlineResources>
       <OnlineResource>
          <URL>http://example.com/doc</URL>
          <Type>Documentation</Type>
       </OnlineResource>
       <OnlineResource>
          <URL>http://example.com/to_be_updated</URL>
          <Type>USE SERVICE API : OPENDAP DATA</Type>
       </OnlineResource>
       <OnlineResource>
          <URL>http://example.com/Browse</URL>
          <Type>Browse</Type>
       </OnlineResource>
    </OnlineResources>
    <Orderable>false</Orderable>
  </Granule>")

(def ^:private update-opendap-url-result
  "Result ECHO10 granule for testing updating OPeNDAP url existing in OnlineResources.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineResources>
      <OnlineResource>
         <URL>http://example.com/foo</URL>
         <Type>USE SERVICE API : OPENDAP DATA</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>http://example.com/doc</URL>
         <Type>Documentation</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>http://example.com/Browse</URL>
         <Type>Browse</Type>
      </OnlineResource>
   </OnlineResources>
   <Orderable>false</Orderable>
</Granule>\n")

(def ^:private update-opendap-type-xml
  "Result ECHO10 granule for testing updating OPeNDAP type existing in OnlineResources.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineResources>
      <OnlineResource>
         <URL>http://example.com/foo</URL>
         <Type>OPENDAP</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>http://example.com/doc</URL>
         <Type>Documentation</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>http://example.com/Browse</URL>
         <Type>Browse</Type>
      </OnlineResource>
   </OnlineResources>
   <Orderable>false</Orderable>
</Granule>\n")

(def ^:private update-opendap-type-xml-result
  "Result ECHO10 granule for testing updating OPeNDAP type existing in OnlineResources.
   Do not format the following as whitespace matters in the string comparison in the test."
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineResources>
      <OnlineResource>
         <URL>http://example.com/foo</URL>
         <Type>USE SERVICE API : OPENDAP DATA</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>http://example.com/doc</URL>
         <Type>Documentation</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>http://example.com/Browse</URL>
         <Type>Browse</Type>
      </OnlineResource>
   </OnlineResources>
   <Orderable>false</Orderable>
</Granule>\n")

(def ^:private update-opendap-other-type-xml-result
  "Result ECHO10 granule for testing updating OPeNDAP type existing in OnlineResources.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineResources>
      <OnlineResource>
         <URL>http://example.com/foo</URL>
         <Type>OPENDAP</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>http://example.com/doc</URL>
         <Type>Documentation</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>http://example.com/Browse</URL>
         <Type>USE SERVICE API : OPENDAP DATA</Type>
      </OnlineResource>
   </OnlineResources>
   <Orderable>false</Orderable>
</Granule>\n")

(def ^:private update-opendap-with-cloud-url-result
  "ECHO10 granule for testing updating OPeNDAP url with on-prem existing in OnlineResources and update with cloud url."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineResources>
      <OnlineResource>
         <URL>http://example.com/to_be_updated</URL>
         <Type>USE SERVICE API : OPENDAP DATA</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>https://opendap.earthdata.nasa.gov/foo</URL>
         <Type>USE SERVICE API : OPENDAP DATA</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>http://example.com/doc</URL>
         <Type>Documentation</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>http://example.com/Browse</URL>
         <Type>Browse</Type>
      </OnlineResource>
   </OnlineResources>
   <Orderable>false</Orderable>
</Granule>\n")

(def ^:private update-opendap-url-preserve-type
  "ECHO10 granule for testing updating OPeNDAP url in OnlineResources, and it preserves the
  existing resource type that is used."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <OnlineResources>
       <OnlineResource>
          <URL>http://example.com/doc</URL>
          <Type>Documentation</Type>
       </OnlineResource>
       <OnlineResource>
          <URL>http://example.com/to_be_updated</URL>
          <Type>USE SERVICE API : OPENDAP DATA (DODS)</Type>
       </OnlineResource>
       <OnlineResource>
          <URL>http://example.com/Browse</URL>
          <Type>Browse</Type>
       </OnlineResource>
    </OnlineResources>
    <Orderable>false</Orderable>
  </Granule>")

(def ^:private update-opendap-url-preserve-type-result
  "Result ECHO10 granule for testing updating OPeNDAP url in OnlineResources and preserve the type.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineResources>
      <OnlineResource>
         <URL>http://example.com/foo</URL>
         <Type>USE SERVICE API : OPENDAP DATA (DODS)</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>http://example.com/doc</URL>
         <Type>Documentation</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>http://example.com/Browse</URL>
         <Type>Browse</Type>
      </OnlineResource>
   </OnlineResources>
   <Orderable>false</Orderable>
</Granule>\n")

(def ^:private update-cloud-opendap-url
  "ECHO10 granule for testing updating Hyrax in the cloud OPeNDAP url existing in OnlineResources."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <OnlineResources>
       <OnlineResource>
          <URL>http://example.com/doc</URL>
          <Type>Documentation</Type>
       </OnlineResource>
       <OnlineResource>
          <URL>https://opendap.earthdata.nasa.gov/to_be_updated</URL>
          <Type>USE SERVICE API : OPENDAP DATA</Type>
       </OnlineResource>
       <OnlineResource>
          <URL>http://example.com/Browse</URL>
          <Type>Browse</Type>
       </OnlineResource>
    </OnlineResources>
    <Orderable>false</Orderable>
  </Granule>")

(def ^:private update-both-opendap-url
  "ECHO10 granule for testing updating Hyrax in the cloud OPeNDAP url existing in OnlineResources."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <OnlineResources>
       <OnlineResource>
          <URL>http://example.com/to_be_updated</URL>
          <Type>USE SERVICE API : OPENDAP DATA</Type>
       </OnlineResource>
       <OnlineResource>
          <URL>http://example.com/doc</URL>
          <Type>Documentation</Type>
       </OnlineResource>
       <OnlineResource>
          <URL>https://opendap.earthdata.nasa.gov/to_be_updated</URL>
          <Type>USE SERVICE API : OPENDAP DATA</Type>
       </OnlineResource>
       <OnlineResource>
          <URL>http://example.com/Browse</URL>
          <Type>Browse</Type>
       </OnlineResource>
    </OnlineResources>
    <Orderable>false</Orderable>
  </Granule>")

(def ^:private update-cloud-opendap-url-result
  "Result ECHO10 granule for testing updating OPeNDAP url existing in OnlineResources.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineResources>
      <OnlineResource>
         <URL>https://opendap.earthdata.nasa.gov/foo</URL>
         <Type>USE SERVICE API : OPENDAP DATA</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>http://example.com/doc</URL>
         <Type>Documentation</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>http://example.com/Browse</URL>
         <Type>Browse</Type>
      </OnlineResource>
   </OnlineResources>
   <Orderable>false</Orderable>
</Granule>\n")

(def ^:private update-cloud-opendap-url-with-on-prem-result
  "Result ECHO10 granule for testing updating OPeNDAP url existing in OnlineResources.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineResources>
      <OnlineResource>
         <URL>http://example.com/foo</URL>
         <Type>USE SERVICE API : OPENDAP DATA</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>https://opendap.earthdata.nasa.gov/to_be_updated</URL>
         <Type>USE SERVICE API : OPENDAP DATA</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>http://example.com/doc</URL>
         <Type>Documentation</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>http://example.com/Browse</URL>
         <Type>Browse</Type>
      </OnlineResource>
   </OnlineResources>
   <Orderable>false</Orderable>
</Granule>\n")

(def ^:private update-cloud-opendap-url-with-both-result
  "Result ECHO10 granule for testing updating OPeNDAP url existing in OnlineResources.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <OnlineResources>
      <OnlineResource>
         <URL>http://example.com/foo</URL>
         <Type>USE SERVICE API : OPENDAP DATA</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>https://opendap.earthdata.nasa.gov/foo</URL>
         <Type>USE SERVICE API : OPENDAP DATA</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>http://example.com/doc</URL>
         <Type>Documentation</Type>
      </OnlineResource>
      <OnlineResource>
         <URL>http://example.com/Browse</URL>
         <Type>Browse</Type>
      </OnlineResource>
   </OnlineResources>
   <Orderable>false</Orderable>
</Granule>\n")

(deftest add-opendap-url
  (testing "add OnlineResources at various places in the xml"
    (are3 [url-value source result]
      (let [grouped-urls (opendap-util/validate-url url-value)]
        (is (= result
               (#'echo10/update-opendap-url-metadata source grouped-urls :replace))))

      "add OnlineResources at the end of the xml"
      "http://example.com/foo"
      add-at-the-end-gran-xml
      add-at-the-end-gran-xml-result

      "add OnlineResources before an element in the xml"
      "http://example.com/foo"
      add-before-element-gran-xml
      add-before-element-gran-xml-result

      "add OnlineResources to empty OnlineResources in the xml"
      "http://example.com/foo"
      add-with-empty-gran-xml
      add-before-element-gran-xml-result

      "add OnlineResources to OnlineResources without OPeNDAP url in the xml"
      "http://example.com/foo"
      add-with-no-match-gran-xml
      add-with-no-match-gran-xml-result

      "update OnlineResources when OPeNDAP url is present"
      "http://example.com/foo"
      update-opendap-url
      update-opendap-url-result

      "update OnlineResources when OPeNDAP url is present and preserves the existing type"
      "http://example.com/foo"
      update-opendap-url-preserve-type
      update-opendap-url-preserve-type-result

      "update with on-prem in metadata, Hyrax-in-the-cloud in update"
      "https://opendap.earthdata.nasa.gov/foo"
      update-opendap-url
      update-opendap-with-cloud-url-result

      "update with Hyrax-in-the-cloud in metadata, Hyrax-in-the-cloud in update"
      "https://opendap.earthdata.nasa.gov/foo"
      update-cloud-opendap-url
      update-cloud-opendap-url-result

      "update with Hyrax-in-the-cloud in metadata, on-prem in update"
      "http://example.com/foo"
      update-cloud-opendap-url
      update-cloud-opendap-url-with-on-prem-result

      "update with both in metadata and on-prem in update"
      "http://example.com/foo"
      update-both-opendap-url
      update-cloud-opendap-url-with-on-prem-result

      "update with both in metadata and Hyrax-in-the-cloud in update"
      "https://opendap.earthdata.nasa.gov/foo"
      update-both-opendap-url
      update-opendap-with-cloud-url-result

      "update with multiple urls, Hyrax-in-the-cloud in metadata"
      "https://opendap.earthdata.nasa.gov/foo, http://example.com/foo"
      update-cloud-opendap-url
      update-cloud-opendap-url-with-both-result

      "update with multiple urls, on-prem in metadata"
      "https://opendap.earthdata.nasa.gov/foo, http://example.com/foo"
      update-opendap-url
      update-cloud-opendap-url-with-both-result

      "update with multiple urls, both Hyrax-in-the-cloud and on-prem in metadata"
      "https://opendap.earthdata.nasa.gov/foo, http://example.com/foo"
      update-both-opendap-url
      update-cloud-opendap-url-with-both-result)))

(deftest append-opendap-url
  (testing "append OnlineResources at various places in the xml"
    (are3 [url-value source result]
      (let [grouped-urls (opendap-util/validate-url url-value)]
        (is (= result
               (#'echo10/update-opendap-url-metadata source grouped-urls :append))))

      "add OnlineResources at the end of the xml"
      "http://example.com/foo"
      add-at-the-end-gran-xml
      add-at-the-end-gran-xml-result

      "add OnlineResources before an element in the xml"
      "http://example.com/foo"
      add-before-element-gran-xml
      add-before-element-gran-xml-result

      "add OnlineResources to empty OnlineResources in the xml"
      "http://example.com/foo"
      add-with-empty-gran-xml
      add-before-element-gran-xml-result

      "add OnlineResources to OnlineResources without OPeNDAP url in the xml"
      "http://example.com/foo"
      add-with-no-match-gran-xml
      add-with-no-match-gran-xml-result

      "update with on-prem in metadata, Hyrax-in-the-cloud in update"
      "https://opendap.earthdata.nasa.gov/foo"
      update-opendap-url
      update-opendap-with-cloud-url-result

      "update with Hyrax-in-the-cloud in metadata, on-prem in update"
      "http://example.com/foo"
      update-cloud-opendap-url
      update-cloud-opendap-url-with-on-prem-result))

  (testing "throws appropriate error messages"
    (are3 [url-value source]
      (let [grouped-urls (opendap-util/validate-url url-value)]
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Update contains conflict"
             (#'echo10/update-opendap-url-metadata source grouped-urls :append))))

      "throws during append OnlineResources when OPeNDAP url is present"
      "http://example.com/foo"
      update-opendap-url

      "OnlineResources when OPeNDAP url is present"
      "http://example.com/foo"
      update-opendap-url-preserve-type

      "update with Hyrax-in-the-cloud in metadata, Hyrax-in-the-cloud in update"
      "https://opendap.earthdata.nasa.gov/foo"
      update-cloud-opendap-url

      "update with both in metadata and on-prem in update"
      "http://example.com/foo"
      update-both-opendap-url

      "update with both in metadata and Hyrax-in-the-cloud in update"
      "https://opendap.earthdata.nasa.gov/foo"
      update-both-opendap-url

      "update with multiple urls, Hyrax-in-the-cloud in metadata"
      "https://opendap.earthdata.nasa.gov/foo, http://example.com/foo"
      update-cloud-opendap-url

      "update with multiple urls, on-prem in metadata"
      "https://opendap.earthdata.nasa.gov/foo, http://example.com/foo"
      update-opendap-url

      "update with multiple urls, both Hyrax-in-the-cloud and on-prem in metadata"
      "https://opendap.earthdata.nasa.gov/foo, http://example.com/foo"
      update-both-opendap-url)))

(deftest update-opendap-type-test
  (testing "using defaults"
    (is (= update-opendap-type-xml-result
           (:metadata (#'echo10/update-opendap-type {:metadata update-opendap-type-xml})))))

  (testing "specifying a specific target type to transform"
    (is (= update-opendap-other-type-xml-result
           (:metadata (#'echo10/update-opendap-type {:metadata update-opendap-type-xml} "Browse"))))))

(deftest update-resource-type-test
  (are3 [resource-type output] (is (= output (#'echo10/update-resource-type resource-type)))
    "opendap updated to USE SERVICE API"
    "OPENDAP" "USE SERVICE API : OPENDAP DATA"

    "GET DATA to USE SERVICE API"
    "GET DATA : OPENDAP DATA" "USE SERVICE API : OPENDAP DATA"

    "GET DATA with sub-type to USE SERVICE API"
    "GET DATA : OPENDAP DATA (DODS)" "USE SERVICE API : OPENDAP DATA (DODS)"

    "handles empty"
    "" "USE SERVICE API : OPENDAP DATA"

    "handles nil"
    nil "USE SERVICE API : OPENDAP DATA"))
