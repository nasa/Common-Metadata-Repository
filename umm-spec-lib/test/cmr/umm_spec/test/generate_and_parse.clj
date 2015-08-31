(ns cmr.umm-spec.test.generate-and-parse
  "Tests roundtrip XML generation from a Clojure record and parsing it. Ensures that the same data
  is returned."
  (:require [clojure.test :refer :all]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [for-all]]
            [cmr.common.util :refer [update-in-each]]
            [cmr.common.test.test-check-ext :as ext :refer [defspec]]
            [cmr.umm-spec.test.expected-conversion :as expected-conversion]
            [cmr.umm-spec.core :as core]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as umm-cmn]
            [clj-time.core :as t]
            [cmr.common.util :refer [are2]]
            [cmr.umm-spec.test.umm-generators :as umm-gen]))

(def example-record
  "An example record with fields supported by most formats."
  (umm-c/map->UMM-C
   {:Platforms [(umm-cmn/map->PlatformType
                 {:ShortName "Platform 1"
                  :LongName "Example Platform Long Name 1"
                  :Type "Aircraft"
                  :Characteristics [(umm-cmn/map->CharacteristicType
                                     {:Name "OrbitalPeriod"
                                      :Description "Orbital period in decimal minutes."
                                      :DataType "float"
                                      :Unit "Minutes"
                                      :Value "96.7"})]
                  :Instruments [(umm-cmn/map->InstrumentType
                                 {:ShortName "An Instrument"
                                  :LongName "The Full Name of An Instrument v123.4"
                                  :Technique "Two cans and a string"
                                  :NumberOfSensors 1
                                  :OperationalModes ["on" "off"]})]})]
    :TemporalExtents [(umm-cmn/map->TemporalExtentType
                       {:TemporalRangeType "temp range"
                        :PrecisionOfSeconds 3
                        :EndsAtPresentFlag false
                        :RangeDateTimes (mapv umm-cmn/map->RangeDateTimeType
                                              [{:BeginningDateTime (t/date-time 2000)
                                                :EndingDateTime (t/date-time 2001)}
                                               {:BeginningDateTime (t/date-time 2002)
                                                :EndingDateTime (t/date-time 2003)}])})]
    :ProcessingLevel (umm-c/map->ProcessingLevelType {})
    :RelatedUrls [(umm-cmn/map->RelatedUrlType {:URLs ["http://google.com"]})]
    :ResponsibleOrganizations [(umm-cmn/map->ResponsibilityType {:Role "RESOURCEPROVIDER"
                                                                 :Party (umm-cmn/map->PartyType {})})]
    :ScienceKeywords [(umm-cmn/map->ScienceKeywordType {:Category "cat" :Topic "top" :Term "ter"})]
    :SpatialExtent (umm-cmn/map->SpatialExtentType {:GranuleSpatialRepresentation "NO_SPATIAL"})

    :EntryId "short_V1"
    :EntryTitle "The entry title V5"
    :Version "V5"
    :DataDates [(umm-cmn/map->DateType {:Date (t/date-time 2012)
                                        :Type "CREATE"})]
    :Abstract "A very abstract collection"
    :DataLanguage "English"
    :Quality "Pretty good quality"}))

(defn xml-round-trip
  "Returns record after being converted to XML and back to UMM through
  the given to-xml and to-umm mappings."
  [record format]
  (core/parse-metadata :collection format (core/generate-metadata :collection format record)))

(deftest roundtrip-gen-parse
  (are2 [metadata-format]
    (= (expected-conversion/convert example-record metadata-format)
       (xml-round-trip example-record metadata-format))

    "echo10"
    :echo10

    "dif9"
    :dif

    "dif10"
    :dif10

    "iso-smap"
    :iso-smap

    "ISO19115-2"
    :iso19115))

(deftest generate-valid-xml
  (testing "valid XML is generated for each format"
    (are [fmt]
        (->> example-record
             (core/generate-metadata :collection fmt)
             (core/validate-xml :collection fmt)
             empty?)
      :echo10
      :dif
      :dif10
      :iso-smap
      :iso19115)))

(defn fixup-generated-collection
  [umm-coll]
  (-> umm-coll
      ;; TODO: right now, the TemporalExtents roundtrip conversion does not work with the generator
      ;; generated umm record. We exclude it from the comparison for now. This should be addressed
      ;; within CMR-1933.
      (assoc :TemporalExtents nil)
      ;; TODO: Platforms/Instruments is not ready yet, but it is generated.
      (update-in-each [:Platforms] assoc :Instruments nil)))

(defspec roundtrip-generator-gen-parse 100
  (for-all [umm-record umm-gen/umm-c-generator
            metadata-format (gen/elements [:echo10 :dif :dif10 :iso-smap :iso19115])]
    (let [expected (fixup-generated-collection (expected-conversion/convert umm-record metadata-format))
          actual   (fixup-generated-collection (xml-round-trip umm-record metadata-format))]
      (is (= expected actual)))))
