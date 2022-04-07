(ns cmr.umm.test.echo10.granule
  "Tests parsing and generating ECHO10 Granule XML."
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :refer [for-all]]

   ; [clojure.test.check.clojure-test :refer [defspec]]
   ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
   [cmr.common.test.test-check-ext :refer [defspec]]

   [cmr.common.date-time-parser :as p]
   [cmr.common.xml :as cx]
   [cmr.umm.echo10.granule :as g]
   [cmr.umm.echo10.echo10-core :as echo10]
   [cmr.umm.test.echo10.echo10-collection-tests :as tc]
   [cmr.umm.test.generators.granule :as gran-gen]
   [cmr.umm.umm-collection :as umm-c]
   [cmr.umm.umm-granule :as umm-g]))

(defn- data-granule->expected
  "Returns the expected data-granule for comparison with the parsed record."
  [data-granule]
  (some-> data-granule
          (assoc :crid-ids nil :feature-ids nil)
          (update :day-night #(if % % "UNSPECIFIED"))))

(defn umm->expected-parsed-echo10
  "Modifies the UMM record for testing ECHO10. ECHO10 contains a subset of the total UMM fields so certain
  fields are removed for comparison of the parsed record"
  [gran]
  (-> gran
      ;; Update the related-urls as ECHO10 OnlineResources' title is built as description plus resource-type
      (update-in [:related-urls] tc/umm-related-urls->expected-related-urls)
      ;; Set crid-ids and feature-ids to nil when data-granule exists since they are not supported in echo10.
      (update-in [:data-granule] data-granule->expected)
      umm-g/map->UmmGranule))

(defspec generate-granule-is-valid-xml-test 100
  (for-all [granule gran-gen/granules]
    (let [xml (echo10/umm->echo10-xml granule)]
      (and
        (> (count xml) 0)
        (= 0 (count (g/validate-xml xml)))))))

(defspec generate-and-parse-granule-test 100
  ;; this is to compare umm->echo10->umm and umm.
  ;; from echo10 to umm, size-unit becomes "MB" regardless of what it is originally.
  ;; Need to strip off the size-unit from both the expected and the actual.
  (for-all [granule gran-gen/granules]
    (let [xml (echo10/umm->echo10-xml granule)
          parsed (g/parse-granule xml)
          parsed (update-in parsed [:data-granule] dissoc :size-unit)
          expected-parsed (umm->expected-parsed-echo10 granule)
          expected-parsed (update-in expected-parsed [:data-granule] dissoc :size-unit)]
      (= parsed expected-parsed))))

(comment

  (def genned (gen/sample gran-gen/granules))
  (def my-gran (first genned))

  ;; this is to compare umm->echo10->umm and umm.
  ;; from echo10 to umm, size-unit becomes "MB" regardless of what it is originally.
  ;; Need to strip off the size-unit from both the expected and the actual.
  (let [xml (echo10/umm->echo10-xml my-gran)
        parsed (g/parse-granule xml)
        parsed (update-in parsed [:data-granule] dissoc :size-unit)
        expected-parsed (umm->expected-parsed-echo10 my-gran)
        expected-parsed (update-in expected-parsed [:data-granule] dissoc :size-unit)]
    (= parsed expected-parsed))

  (let [xml (echo10/umm->echo10-xml my-gran)]
    (and
     (> (count xml) 0)
     (= 0 (count (g/validate-xml xml)))))

  )

(def all-fields-granule-xml
  "<Granule>
    <GranuleUR>GranuleUR100</GranuleUR>
    <InsertTime>1999-12-30T19:00:00-05:00</InsertTime>
    <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
    <DeleteTime>2000-12-31T19:00:00-05:00</DeleteTime>
    <Collection>
      <DataSetId>R1_SCANSAR_FRAME</DataSetId>
    </Collection>
    <RestrictionFlag>5.3</RestrictionFlag>
    <DataGranule>
      <DataGranuleSizeInBytes>71938553</DataGranuleSizeInBytes>
      <SizeMBDataGranule>71.93855287</SizeMBDataGranule>
      <Checksum>
        <Value>1234567890</Value>
        <Algorithm>Fletcher-32</Algorithm>
      </Checksum>
      <ProducerGranuleId>0000000.0000001.hdf</ProducerGranuleId>
      <DayNightFlag>NIGHT</DayNightFlag>
    </DataGranule>
    <PGEVersionClass>
      <PGEName>Sentinel</PGEName>
      <PGEVersion>1.4.1</PGEVersion>
    </PGEVersionClass>
    <Orderable>true</Orderable>
    <Temporal>
      <RangeDateTime>
        <BeginningDateTime>1996-02-24T22:20:41-05:00</BeginningDateTime>
        <EndingDateTime>1997-03-24T22:20:41-05:00</EndingDateTime>
      </RangeDateTime>
      <SingleDateTime>2010-01-05T05:30:30.550-05:00</SingleDateTime>
    </Temporal>
    <MeasuredParameters>
      <MeasuredParameter>
        <ParameterName>Surface_Elevation</ParameterName>
        <QAStats>
          <QAPercentMissingData>5</QAPercentMissingData>
          <QAPercentOutOfBoundsData>0</QAPercentOutOfBoundsData>
          <QAPercentInterpolatedData>10</QAPercentInterpolatedData>
          <QAPercentCloudCover>20</QAPercentCloudCover>
        </QAStats>
        <QAFlags>
          <AutomaticQualityFlag>Passed</AutomaticQualityFlag>
          <AutomaticQualityFlagExplanation>Passed indicates parameter passed for specific automatic test; Suspect, QA not run; Failed, parameter failed specific automatic test.</AutomaticQualityFlagExplanation>
          <OperationalQualityFlag>Inferred Passed Operational</OperationalQualityFlag>
          <OperationalQualityFlagExplanation>Passed,parameter passed the specified operational test. Inferred Pass,parameter terminated with warnings. Failed parameter terminated with fatal errors.</OperationalQualityFlagExplanation>
          <ScienceQualityFlag>Inferred Passed Science</ScienceQualityFlag>
          <ScienceQualityFlagExplanation>Passed,parameter passed the specified science test. Inferred Pass,parameter terminated with warnings for specified science test. Failed parameter terminated with fatal errors for specified science test.</ScienceQualityFlagExplanation>
        </QAFlags>
      </MeasuredParameter>
      <MeasuredParameter>
        <ParameterName>Surface_Reflectance</ParameterName>
        <QAStats>
          <QAPercentMissingData>6</QAPercentMissingData>
          <QAPercentOutOfBoundsData>1</QAPercentOutOfBoundsData>
        </QAStats>
      </MeasuredParameter>
    </MeasuredParameters>
    <Platforms>
      <Platform>
        <ShortName>RADARSAT-1</ShortName>
        <Instruments>
          <Instrument>
            <ShortName>SAR</ShortName>
            <Characteristics>
              <Characteristic>
                <Name>Characteristic #1</Name>
                <Value>Characteristic I</Value>
              </Characteristic>
              <Characteristic>
                <Name>Characteristic #2</Name>
                <Value>Characteristic II</Value>
              </Characteristic>
            </Characteristics>
            <Sensors>
              <Sensor>
                <ShortName>SNA</ShortName>
                <Characteristics>
                  <Characteristic>
                    <Name>Characteristic #3</Name>
                    <Value>Characteristic III</Value>
                  </Characteristic>
                  <Characteristic>
                    <Name>Characteristic #4</Name>
                    <Value>Characteristic IV</Value>
                  </Characteristic>
                </Characteristics>
              </Sensor>
              <Sensor>
                <ShortName>SNB</ShortName>
              </Sensor>
            </Sensors>
            <OperationModes>
              <OperationMode>Antarctic</OperationMode>
              <OperationMode>Arctic</OperationMode>
            </OperationModes>
          </Instrument>
          <Instrument>
            <ShortName>MAR</ShortName>
          </Instrument>
        </Instruments>
      </Platform>
      <Platform>
        <ShortName>RADARSAT-2</ShortName>
      </Platform>
    </Platforms>
    <OrbitCalculatedSpatialDomains>
      <OrbitCalculatedSpatialDomain>
        <OrbitalModelName>OrbitalModelName</OrbitalModelName>
        <OrbitNumber>0</OrbitNumber>
        <StartOrbitNumber>0</StartOrbitNumber>
        <StopOrbitNumber>0</StopOrbitNumber>
        <EquatorCrossingLongitude>0.0</EquatorCrossingLongitude>
        <EquatorCrossingDateTime>2010-01-05T05:30:30Z</EquatorCrossingDateTime>
      </OrbitCalculatedSpatialDomain>
      <OrbitCalculatedSpatialDomain>
        <OrbitalModelName>OrbitalModelName</OrbitalModelName>
        <OrbitNumber>0</OrbitNumber>
        <StartOrbitNumber>0</StartOrbitNumber>
        <StopOrbitNumber>0</StopOrbitNumber>
        <EquatorCrossingLongitude>0.0</EquatorCrossingLongitude>
        <EquatorCrossingDateTime>2010-01-05T05:30:30Z</EquatorCrossingDateTime>
      </OrbitCalculatedSpatialDomain>
    </OrbitCalculatedSpatialDomains>
    <Campaigns>
      <Campaign>
        <ShortName>Short Name-240</ShortName>
      </Campaign>
      <Campaign>
        <ShortName>Short Name-241</ShortName>
      </Campaign>
    </Campaigns>
    <TwoDCoordinateSystem>
      <StartCoordinate1>1.0</StartCoordinate1>
      <EndCoordinate1>2.0</EndCoordinate1>
      <StartCoordinate2>3.0</StartCoordinate2>
      <EndCoordinate2>4.0</EndCoordinate2>
      <TwoDCoordinateSystemName>name0</TwoDCoordinateSystemName>
    </TwoDCoordinateSystem>
    <OnlineAccessURLs>
      <OnlineAccessURL>
        <URL>http://ghrc.nsstc.nasa.gov/hydro/details.pl?ds=dc8capac</URL>
      </OnlineAccessURL>
    </OnlineAccessURLs>
    <OnlineAccessURLs>
      <OnlineAccessURL>
        <URL>s3://aws.com/hydro/details</URL>
      </OnlineAccessURL>
    </OnlineAccessURLs>
    <OnlineResources>
      <OnlineResource>
        <URL>http://camex.nsstc.nasa.gov/camex3/</URL>
        <Type>DATA ACCESS</Type>
      </OnlineResource>
      <OnlineResource>
        <URL>http://ghrc.nsstc.nasa.gov/uso/ds_docs/camex3/dc8capac/dc8capac_dataset.html</URL>
        <Type>Guide</Type>
        <MimeType>Text/html</MimeType>
      </OnlineResource>
      <OnlineResource>
        <URL>ftp://camex.nsstc.nasa.gov/camex3/dc8capac/browse/</URL>
        <Description>Some description.</Description>
        <Type>Browse</Type>
      </OnlineResource>
      <OnlineResource>
        <URL>https://dmr.s3.bucket.example.org</URL>
        <Description>Some description about DMR buckets</Description>
        <Type>EXTENDED METADATA : DMR++</Type>
      </OnlineResource>
    </OnlineResources>
    <CloudCover>0.8</CloudCover>
    <AssociatedBrowseImageUrls>
      <ProviderBrowseUrl>
        <URL>http://nasa.gov/1</URL>
        <FileSize>101</FileSize>
        <Description>A file 1</Description>
      </ProviderBrowseUrl>
      <ProviderBrowseUrl>
        <URL>http://nasa.gov/2</URL>
        <FileSize>102</FileSize>
        <Description>A file 2</Description>
      </ProviderBrowseUrl>
    </AssociatedBrowseImageUrls>
  </Granule>")

(def expected-temporal
  (umm-g/map->GranuleTemporal
    {:range-date-time
     (umm-c/map->RangeDateTime
       {:beginning-date-time (p/parse-datetime "1996-02-24T22:20:41-05:00")
        :ending-date-time (p/parse-datetime "1997-03-24T22:20:41-05:00")})
     :single-date-time (p/parse-datetime "2010-01-05T05:30:30.550-05:00")}))

(def expected-granule
  (umm-g/map->UmmGranule
    {:granule-ur "GranuleUR100"
     :data-provider-timestamps (umm-g/map->DataProviderTimestamps
                                 {:insert-time (p/parse-datetime "1999-12-30T19:00:00-05:00")
                                  :update-time (p/parse-datetime "1999-12-31T19:00:00-05:00")
                                  :delete-time (p/parse-datetime "2000-12-31T19:00:00-05:00")})
     :collection-ref (umm-g/map->CollectionRef
                       {:entry-title "R1_SCANSAR_FRAME"})
     :access-value 5.3
     :data-granule (umm-g/map->DataGranule
                     {:size-in-bytes 71938553
                      :size 71.93855287
                      :size-unit "MB"
                      :checksum (umm-g/map->Checksum
                                  {:value "1234567890"
                                   :algorithm "Fletcher-32"})
                      :producer-gran-id "0000000.0000001.hdf"
                      :day-night "NIGHT"})
     :pge-version-class (umm-g/map->PGEVersionClass
                         {:pge-name "Sentinel"
                          :pge-version "1.4.1"})
     :project-refs ["Short Name-240" "Short Name-241"]
     :cloud-cover 0.8
     :temporal expected-temporal
     :measured-parameters
     [(umm-g/map->MeasuredParameter
        {:parameter-name "Surface_Elevation"
         :qa-stats (umm-g/map->QAStats
                     {:qa-percent-missing-data 5.0
                      :qa-percent-out-of-bounds-data 0.0
                      :qa-percent-interpolated-data 10.0
                      :qa-percent-cloud-cover 20.0})
         :qa-flags (umm-g/map->QAFlags
                     {:automatic-quality-flag "Passed"
                      :automatic-quality-flag-explanation "Passed indicates parameter passed for specific automatic test; Suspect, QA not run; Failed, parameter failed specific automatic test."
                      :operational-quality-flag "Inferred Passed Operational"
                      :operational-quality-flag-explanation "Passed,parameter passed the specified operational test. Inferred Pass,parameter terminated with warnings. Failed parameter terminated with fatal errors."
                      :science-quality-flag "Inferred Passed Science"
                      :science-quality-flag-explanation "Passed,parameter passed the specified science test. Inferred Pass,parameter terminated with warnings for specified science test. Failed parameter terminated with fatal errors for specified science test."})})
      (umm-g/map->MeasuredParameter
        {:parameter-name "Surface_Reflectance"
         :qa-stats (umm-g/map->QAStats
                     {:qa-percent-missing-data 6.0
                      :qa-percent-out-of-bounds-data 1.0})})]
     :platform-refs
     [(umm-g/map->PlatformRef
        {:short-name "RADARSAT-1"
         :instrument-refs
         [(umm-g/map->InstrumentRef
            {:short-name "SAR"
             :characteristic-refs [(umm-g/map->CharacteristicRef
                                     {:name "Characteristic #1"
                                      :value "Characteristic I"})
                                   (umm-g/map->CharacteristicRef
                                     {:name "Characteristic #2"
                                      :value "Characteristic II"})]
             :sensor-refs [(umm-g/map->SensorRef
                             {:short-name "SNA"
                              :characteristic-refs [(umm-g/map->CharacteristicRef
                                                      {:name "Characteristic #3"
                                                       :value "Characteristic III"})
                                                    (umm-g/map->CharacteristicRef
                                                      {:name "Characteristic #4"
                                                       :value "Characteristic IV"})]})
                           (umm-g/map->SensorRef {:short-name "SNB"})]
             :operation-modes ["Antarctic" "Arctic"]})
          (umm-g/map->InstrumentRef {:short-name "MAR"})]})
      (umm-g/map->PlatformRef
        {:short-name "RADARSAT-2"
         :instrument-refs nil})]
     :orbit-calculated-spatial-domains [(umm-g/map->OrbitCalculatedSpatialDomain
                                          {:orbital-model-name "OrbitalModelName"
                                           :orbit-number 0
                                           :start-orbit-number 0
                                           :stop-orbit-number 0
                                           :equator-crossing-longitude 0.0
                                           :equator-crossing-date-time (p/parse-datetime "2010-01-05T05:30:30Z")})
                                        (umm-g/map->OrbitCalculatedSpatialDomain
                                          {:orbital-model-name "OrbitalModelName"
                                           :orbit-number 0
                                           :start-orbit-number 0
                                           :stop-orbit-number 0
                                           :equator-crossing-longitude 0.0
                                           :equator-crossing-date-time (p/parse-datetime "2010-01-05T05:30:30Z")})]
     :two-d-coordinate-system (umm-g/map->TwoDCoordinateSystem
                                {:name "name0"
                                 :start-coordinate-1 1.0
                                 :end-coordinate-1 2.0
                                 :start-coordinate-2 3.0
                                 :end-coordinate-2 4.0})
     :related-urls [(umm-c/map->RelatedURL
                      {:type "GET DATA"
                       :url "http://ghrc.nsstc.nasa.gov/hydro/details.pl?ds=dc8capac"})
                    (umm-c/map->RelatedURL
                      {:type "GET DATA VIA DIRECT ACCESS"
                       :url "s3://aws.com/hydro/details"})
                    (umm-c/map->RelatedURL
                      {:type "GET DATA"
                       :title "(DATA ACCESS)"
                       :url "http://camex.nsstc.nasa.gov/camex3/"})
                    (umm-c/map->RelatedURL
                      {:type "VIEW RELATED INFORMATION"
                       :sub-type "USER'S GUIDE"
                       :mime-type "Text/html"
                       :title "(Guide)"
                       :url "http://ghrc.nsstc.nasa.gov/uso/ds_docs/camex3/dc8capac/dc8capac_dataset.html"})
                    (umm-c/map->RelatedURL
                      {:type "GET RELATED VISUALIZATION"
                       :url "ftp://camex.nsstc.nasa.gov/camex3/dc8capac/browse/"
                       :description "Some description."
                       :title "Some description. (Browse)"})
                    (umm-c/map->RelatedURL
                      {:type "EXTENDED METADATA"
                       :sub-type "DMR++"
                       :url "https://dmr.s3.bucket.example.org"
                       :title "Some description about DMR buckets (EXTENDED METADATA : DMR++)"
                       :description "Some description about DMR buckets"})
                    (umm-c/map->RelatedURL
                      {:type "GET RELATED VISUALIZATION"
                       :url "http://nasa.gov/1"
                       :description "A file 1"
                       :title "A file 1"
                       :size 101})
                    (umm-c/map->RelatedURL
                      {:type "GET RELATED VISUALIZATION"
                       :url "http://nasa.gov/2"
                       :description "A file 2"
                       :title "A file 2"
                       :size 102})]}))

(deftest parse-granule-test
  (testing "parse granule"
    (is (= expected-granule (g/parse-granule all-fields-granule-xml))))
  (testing "parse temporal"
    (is (= expected-temporal (g/parse-temporal all-fields-granule-xml))))
  (testing "parse access value"
    (is (= 5.3 (g/parse-access-value all-fields-granule-xml)))))


(def valid-granule-xml-w-datasetid
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <DataSetId>AQUARIUS_L1A_SSS</DataSetId>
    </Collection>
    <RestrictionFlag>0.0</RestrictionFlag>
    <Orderable>false</Orderable>
  </Granule>")

(def valid-granule-xml-w-sn-ver
  "<Granule>
    <GranuleUR>GranuleUR100</GranuleUR>
    <InsertTime>2010-01-05T05:30:30.550-05:00</InsertTime>
    <LastUpdate>2010-01-05T05:30:30.550-05:00</LastUpdate>
    <Collection>
      <ShortName>TESTCOLL-100</ShortName>
      <VersionId>1.0</VersionId>
    </Collection>
    <RestrictionFlag>0.0</RestrictionFlag>
    <Orderable>true</Orderable>
  </Granule>")

(def valid-granule-xml-w-entryid
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <RestrictionFlag>0.0</RestrictionFlag>
    <Orderable>false</Orderable>
  </Granule>")

(def invalid-collection-ref-granule-xml
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <DataSetId>AQUARIUS_L1A_SSS</DataSetId>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <RestrictionFlag>0.0</RestrictionFlag>
    <Orderable>false</Orderable>
  </Granule>")

(deftest validate-xml
  (testing "valid granule collection ref with dataset id"
    (is (empty? (g/validate-xml valid-granule-xml-w-datasetid))))
  (testing "valid granule collection ref with short name and version id"
    (is (empty? (g/validate-xml valid-granule-xml-w-sn-ver))))
  (testing "valid granule collection ref with entry id"
    (is (empty? (g/validate-xml valid-granule-xml-w-entryid))))
  (testing "invalid granule collection ref with multiple choice fields"
    (is (= ["Exception while parsing invalid XML: Line 7 - cvc-complex-type.2.4.d: Invalid content was found starting with element 'EntryId'. No child element is expected at this point."]
           (g/validate-xml invalid-collection-ref-granule-xml))))
  (testing "invalid xml"
    (is (= ["Exception while parsing invalid XML: Line 3 - cvc-datatype-valid.1.2.1: 'XXXX-01-05T05:30:30.550-05:00' is not a valid value for 'dateTime'."
            "Exception while parsing invalid XML: Line 3 - cvc-type.3.1.3: The value 'XXXX-01-05T05:30:30.550-05:00' of element 'InsertTime' is not valid."
            "Exception while parsing invalid XML: Line 4 - cvc-datatype-valid.1.2.1: 'XXXX-01-05T05:30:30.550-05:00' is not a valid value for 'dateTime'."
            "Exception while parsing invalid XML: Line 4 - cvc-type.3.1.3: The value 'XXXX-01-05T05:30:30.550-05:00' of element 'LastUpdate' is not valid."]
           (g/validate-xml (string/replace valid-granule-xml-w-sn-ver "2010" "XXXX"))))))

(deftest generate-data-granule-test
  (testing "Testing the generate-data-granule method when the size doesn't exist - making sure that
            a null pointer exception isn't thrown. CMR-7605"
    (let [granule-data (g/generate-data-granule
                         (umm-g/map->DataGranule
                           {:size-in-bytes 71938553
                            :size-unit "KB"
                            :producer-gran-id "0000000.0000001.hdf"
                            :day-night "NIGHT"}))]
      (is (= (cx/string-at-path granule-data [:DataGranuleSizeInBytes])
             "71938553"))
      (is (= (cx/string-at-path granule-data [:Size])
             nil)))))
