(ns cmr.search.test.perf.echo10.granule
  "Tests performance of converting granules from ECHO 10 XML to UMM JSON"
  (:require
   [clojure.test :refer :all]
   [criterium.core :as criterium]
   [cmr.common.mime-types :as mime-types]
   [cmr.search.data.metadata-retrieval.metadata-transformer :as metadata-transformer]
   [cmr.umm.echo10.granule :as g]))

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

(def echo10-concept
  "A fake concept map with echo10 metadata"
  {:concept-id "G1200000002-PROV1"
   :revision-id 1
   :metadata all-fields-granule-xml
   :format "application/echo10+xml"
   :concept-type :granule})

;; (deftest parse-granule-perf
;;   (testing "parse granule performance"
;;     (is (< 0.005 (first (:mean (criterium/benchmark (g/parse-granule all-fields-granule-xml) {:verbose true})))))))

;; (deftest parse-granule-perf-out
;;   (testing "parse granule performance out"
;;     (is nil (criterium/bench (g/parse-granule all-fields-granule-xml)))))

;; (deftest parse-granule-perf
;;   (testing "parse granule performance out"
;;     (is (< (first (:mean (let [result (criterium/benchmark (g/parse-granule all-fields-granule-xml) {:verbose true})]
;;                            (criterium/report-result result {:verbose true})
;;                            result)))
;;            0.005))))

;; context concept target-format
(deftest parse-granule-perf
  (testing "parse granule performance out"
    (is (< (first (:mean (let [result (criterium/benchmark (metadata-transformer/transform nil echo10-concept :umm-json) {:verbose true})]
                           (criterium/report-result result {:verbose true})
                           result)))
           0.005))))

(deftest parse-granule-umm-lib-perf
  (testing "parse granule umm-lib performance out"
    (is (< (first (:mean (let [result (criterium/benchmark (g/parse-granule all-fields-granule-xml) {:verbose true})]
                           (criterium/report-result result {:verbose true})
                           result)))
           0.005))))


;; (clojure.test/test-vars [#'cmr.search.test.perf.echo10.granule/parse-granule-perf])
;; (clojure.test/test-vars [#'cmr.search.test.perf.echo10.granule/parse-granule-umm-lib-perf])

