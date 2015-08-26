(ns cmr.umm.test.echo10.granule
  "Tests parsing and generating ECHO10 Granule XML."
  (:require [clojure.test :refer :all]

            ; [clojure.test.check.clojure-test :refer [defspec]]
            ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
            [cmr.common.test.test-check-ext :refer [defspec]]

            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]
            [clojure.string :as s]
            [cmr.umm.test.generators.granule :as gran-gen]
            [cmr.common.date-time-parser :as p]
            [cmr.umm.echo10.granule :as g]
            [cmr.umm.echo10.core :as echo10]
            [cmr.umm.collection :as umm-c]
            [cmr.umm.granule :as umm-g]
            [cmr.umm.test.echo10.collection :as tc]))

(defn umm->expected-parsed-echo10
  "Modifies the UMM record for testing ECHO10. ECHO10 contains a subset of the total UMM fields so certain
  fields are removed for comparison of the parsed record"
  [gran]
  (-> gran
      ;; Update the related-urls as ECHO10 OnlineResources' title is built as description plus resource-type
      (update-in [:related-urls] tc/umm-related-urls->expected-related-urls)
      umm-g/map->UmmGranule))

(defspec generate-granule-is-valid-xml-test 100
  (for-all [granule gran-gen/granules]
    (let [xml (echo10/umm->echo10-xml granule)]
      (and
        (> (count xml) 0)
        (= 0 (count (g/validate-xml xml)))))))

(defspec generate-and-parse-granule-test 100
  (for-all [granule gran-gen/granules]
    (let [xml (echo10/umm->echo10-xml granule)
          parsed (g/parse-granule xml)
          expected-parsed (umm->expected-parsed-echo10 granule)]
      (= parsed expected-parsed))))

(def all-fields-granule-xml
  "<Granule>
    <GranuleUR>GranuleUR100</GranuleUR>
    <InsertTime>1999-12-30T19:00:00-05:00</InsertTime>
    <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
    <DeleteTime>2000-12-31T19:00:00-05:00</DeleteTime>
    <Collection>
      <DataSetId>R1_SCANSAR_FRAME</DataSetId>
    </Collection>
    <DataGranule>
      <ProducerGranuleId>0000000.0000001.hdf</ProducerGranuleId>
      <DayNightFlag>NIGHT</DayNightFlag>
    </DataGranule>
    <Orderable>true</Orderable>
    <Temporal>
      <RangeDateTime>
        <BeginningDateTime>1996-02-24T22:20:41-05:00</BeginningDateTime>
        <EndingDateTime>1997-03-24T22:20:41-05:00</EndingDateTime>
      </RangeDateTime>
      <SingleDateTime>2010-01-05T05:30:30.550-05:00</SingleDateTime>
    </Temporal>
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
        <StartOrbitNumber>0.0</StartOrbitNumber>
        <StopOrbitNumber>0.0</StopOrbitNumber>
        <EquatorCrossingLongitude>0.0</EquatorCrossingLongitude>
        <EquatorCrossingDateTime>2010-01-05T05:30:30Z</EquatorCrossingDateTime>
      </OrbitCalculatedSpatialDomain>
      <OrbitCalculatedSpatialDomain>
        <OrbitalModelName>OrbitalModelName</OrbitalModelName>
        <OrbitNumber>0</OrbitNumber>
        <StartOrbitNumber>0.0</StartOrbitNumber>
        <StopOrbitNumber>0.0</StopOrbitNumber>
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

(deftest parse-granule-test
  (let [expected (umm-g/map->UmmGranule
                   {:granule-ur "GranuleUR100"
                    :data-provider-timestamps (umm-c/map->DataProviderTimestamps
                                                {:insert-time (p/parse-datetime "1999-12-30T19:00:00-05:00")
                                                 :update-time (p/parse-datetime "1999-12-31T19:00:00-05:00")
                                                 :delete-time (p/parse-datetime "2000-12-31T19:00:00-05:00")})
                    :collection-ref (umm-g/map->CollectionRef
                                      {:entry-title "R1_SCANSAR_FRAME"})
                    :data-granule (umm-g/map->DataGranule
                                    {:producer-gran-id "0000000.0000001.hdf"
                                     :day-night "NIGHT"})
                    :project-refs ["Short Name-240" "Short Name-241"]
                    :cloud-cover 0.8
                    :temporal
                    (umm-g/map->GranuleTemporal
                      {:range-date-time
                       (umm-c/map->RangeDateTime
                         {:beginning-date-time (p/parse-datetime "1996-02-24T22:20:41-05:00")
                          :ending-date-time (p/parse-datetime "1997-03-24T22:20:41-05:00")})
                       :single-date-time (p/parse-datetime "2010-01-05T05:30:30.550-05:00")})
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
                                                          :start-orbit-number 0.0
                                                          :stop-orbit-number 0.0
                                                          :equator-crossing-longitude 0.0
                                                          :equator-crossing-date-time (p/parse-datetime "2010-01-05T05:30:30Z")})
                                                       (umm-g/map->OrbitCalculatedSpatialDomain
                                                         {:orbital-model-name "OrbitalModelName"
                                                          :orbit-number 0
                                                          :start-orbit-number 0.0
                                                          :stop-orbit-number 0.0
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
                                      :size 102})]})
        actual (g/parse-granule all-fields-granule-xml)]
    (is (= expected actual))))


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
    (is (= ["Line 7 - cvc-complex-type.2.4.d: Invalid content was found starting with element 'EntryId'. No child element is expected at this point."]
           (g/validate-xml invalid-collection-ref-granule-xml))))
  (testing "invalid xml"
    (is (= ["Line 3 - cvc-datatype-valid.1.2.1: 'XXXX-01-05T05:30:30.550-05:00' is not a valid value for 'dateTime'."
            "Line 3 - cvc-type.3.1.3: The value 'XXXX-01-05T05:30:30.550-05:00' of element 'InsertTime' is not valid."
            "Line 4 - cvc-datatype-valid.1.2.1: 'XXXX-01-05T05:30:30.550-05:00' is not a valid value for 'dateTime'."
            "Line 4 - cvc-type.3.1.3: The value 'XXXX-01-05T05:30:30.550-05:00' of element 'LastUpdate' is not valid."]
           (g/validate-xml (s/replace valid-granule-xml-w-sn-ver "2010" "XXXX"))))))

