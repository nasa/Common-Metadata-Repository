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
            [cmr.umm.granule :as umm-g]))

(comment
(gen/sample gran-gen/granules)
)

(defspec generate-granule-is-valid-xml-test 100
  (for-all [granule gran-gen/granules]
    (let [xml (echo10/umm->echo10-xml granule)]
      (and
        (> (count xml) 0)
        (= 0 (count (g/validate-xml xml)))))))

(defspec generate-and-parse-granule-test 100
  (for-all [granule gran-gen/granules]
    (let [xml (echo10/umm->echo10-xml granule)
          parsed (g/parse-granule xml)]
      (= parsed granule))))

(def all-fields-granule-xml
  "<Granule>
  <GranuleUR>GranuleUR100</GranuleUR>
  <InsertTime>2010-01-05T05:30:30.550-05:00</InsertTime>
  <LastUpdate>2010-01-05T05:30:30.550-05:00</LastUpdate>
  <Collection>
    <DataSetId>R1_SCANSAR_FRAME</DataSetId>
  </Collection>
  <DataGranule>
    <ProducerGranuleId>0000000.0000001.hdf</ProducerGranuleId>
  </DataGranule>
  <Orderable>true</Orderable>
  <Temporal>
      <RangeDateTime>
        <BeginningDateTime>1996-02-24T22:20:41-05:00</BeginningDateTime>
        <EndingDateTime>1997-03-24T22:20:41-05:00</EndingDateTime>
      </RangeDateTime>
      <SingleDateTime>2010-01-05T05:30:30.550-05:00</SingleDateTime>
    </Temporal>
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
    </OnlineResource>
    <OnlineResource>
      <URL>ftp://camex.nsstc.nasa.gov/camex3/dc8capac/browse/</URL>
      <Description>Some description.</Description>
      <Type>Browse</Type>
    </OnlineResource>
  </OnlineResources>
  <CloudCover>0.8</CloudCover>
  </Granule>")

(deftest parse-granule-test
  (let [expected (umm-g/map->UmmGranule
                   {:granule-ur "GranuleUR100"
                    :collection-ref (umm-g/map->CollectionRef
                                      {:entry-title "R1_SCANSAR_FRAME"})
                    :data-granule (umm-g/map->DataGranule
                                    {:producer-gran-id "0000000.0000001.hdf"})
                    :project-refs ["Short Name-240" "Short Name-241"]
                    :cloud-cover 0.8
                    :temporal
                    (umm-g/map->GranuleTemporal
                      {:range-date-time
                       (umm-c/map->RangeDateTime
                         {:beginning-date-time (p/parse-datetime "1996-02-24T22:20:41-05:00")
                          :ending-date-time (p/parse-datetime "1997-03-24T22:20:41-05:00")})
                       :single-date-time (p/parse-datetime "2010-01-05T05:30:30.550-05:00")})
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
                    :related-urls [(umm-g/map->RelatedURL
                                     {:type "GET DATA"
                                      :url "http://ghrc.nsstc.nasa.gov/hydro/details.pl?ds=dc8capac"})
                                   (umm-g/map->RelatedURL
                                     {:type "GET DATA"
                                      :url "http://camex.nsstc.nasa.gov/camex3/"})
                                   (umm-g/map->RelatedURL
                                     {:type "VIEW RELATED INFORMATION"
                                      :sub-type "USER'S GUIDE"
                                      :url "http://ghrc.nsstc.nasa.gov/uso/ds_docs/camex3/dc8capac/dc8capac_dataset.html"})
                                   (umm-g/map->RelatedURL
                                     {:type "GET RELATED VISUALIZATION"
                                      :url "ftp://camex.nsstc.nasa.gov/camex3/dc8capac/browse/"
                                      :description "Some description."})]})
        actual (g/parse-granule all-fields-granule-xml)]
    (is (= expected actual))))


(def valid-granule-xml-w-datasetid
  "<Granule>
  <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
  <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
  <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
  <Collection>
  <DataSetId>AQUARIUS_L1A_SSS:1</DataSetId>
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


(deftest validate-xml
  (testing "valid xml1"
    (is (= 0 (count (g/validate-xml valid-granule-xml-w-datasetid)))))
  (testing "valid xml2"
    (is (= 0 (count (g/validate-xml valid-granule-xml-w-sn-ver)))))
  (testing "invalid xml"
    (is (= ["Line 3 - cvc-datatype-valid.1.2.1: 'XXXX-01-05T05:30:30.550-05:00' is not a valid value for 'dateTime'."
            "Line 3 - cvc-type.3.1.3: The value 'XXXX-01-05T05:30:30.550-05:00' of element 'InsertTime' is not valid."
            "Line 4 - cvc-datatype-valid.1.2.1: 'XXXX-01-05T05:30:30.550-05:00' is not a valid value for 'dateTime'."
            "Line 4 - cvc-type.3.1.3: The value 'XXXX-01-05T05:30:30.550-05:00' of element 'LastUpdate' is not valid."]
           (g/validate-xml (s/replace valid-granule-xml-w-sn-ver "2010" "XXXX"))))))

