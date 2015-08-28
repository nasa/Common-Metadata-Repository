(ns cmr.umm-spec.test.generate-and-parse
  "Tests roundtrip XML generation from a Clojure record and parsing it. Ensures that the same data
  is returned."
  (:require [clojure.test :refer :all]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [for-all]]
            [cmr.common.test.test-check-ext :as ext :refer [defspec]]
            [cmr.umm-spec.test.expected-conversion :as expected-conversion]
            [cmr.umm-spec.core :as core]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as umm-cmn]
            [clj-time.core :as t]
            [cmr.common.util :refer [are2]]
            [cmr.umm-spec.test.umm-generators :as umm-gen]))

(def example-base
  "This contains an base example record with fields supported by all formats."
  (umm-c/map->UMM-C
    {:Platforms [(umm-cmn/map->PlatformType
                   {:ShortName "Platform"
                    :Instruments [(umm-cmn/map->InstrumentType {:ShortName "Instrument"})]})]
     :ProcessingLevel (umm-c/map->ProcessingLevelType {})
     :RelatedUrls [(umm-cmn/map->RelatedUrlType {:URLs ["http://google.com"]})]
     :ResponsibleOrganizations [(umm-cmn/map->ResponsibilityType {:Role "RESOURCEPROVIDER"
                                                                  :Party (umm-cmn/map->PartyType {})})]
     :ScienceKeywords [(umm-cmn/map->ScienceKeywordType {:Category "cat" :Topic "top" :Term "ter"})]
     :SpatialExtent (umm-cmn/map->SpatialExtentType {:GranuleSpatialRepresentation "NO_SPATIAL"})
     :AccessConstraints (umm-cmn/map->AccessConstraintsType
                          {:Description "Access constraint description"
                           :Value "0"})

     :EntryId "short_V1"
     :EntryTitle "The entry title V5"
     :Version "V5"
     :Abstract "A very abstract collection"
     :DataLanguage "English"
     :Quality "Pretty good quality"}))

(def temporal-extents
  "A map of example UMM TemporalExtents."
  {"Range Dates"
   (umm-cmn/map->TemporalExtentType
     {:TemporalRangeType "temp range"
      :PrecisionOfSeconds 3
      :EndsAtPresentFlag false
      :RangeDateTimes (mapv umm-cmn/map->RangeDateTimeType
                            [{:BeginningDateTime (t/date-time 2000)
                              :EndingDateTime (t/date-time 2001)}
                             {:BeginningDateTime (t/date-time 2002)
                              :EndingDateTime (t/date-time 2003)}])})
   "Single dates"
   (umm-cmn/map->TemporalExtentType
     {:TemporalRangeType "temp range"
      :PrecisionOfSeconds 3
      :EndsAtPresentFlag false
      :SingleDateTimes [(t/date-time 2003) (t/date-time 2004)]})

   "Periodic dates"
   (umm-cmn/map->TemporalExtentType
     {:TemporalRangeType "temp range"
      :PrecisionOfSeconds 3
      :EndsAtPresentFlag false
      :PeriodicDateTimes (mapv umm-cmn/map->PeriodicDateTimeType
                               [{:Name "period1"
                                 :StartDate (t/date-time 2000)
                                 :EndDate (t/date-time 2001)
                                 :DurationUnit "YEAR"
                                 :DurationValue 4
                                 :PeriodCycleDurationUnit "DAY"
                                 :PeriodCycleDurationValue 3}
                                {:Name "period2"
                                 :StartDate (t/date-time 2000)
                                 :EndDate (t/date-time 2001)
                                 :DurationUnit "YEAR"
                                 :DurationValue 4
                                 :PeriodCycleDurationUnit "DAY"
                                 :PeriodCycleDurationValue 3}])})})

(def example-records
  "A seq of example records for each of the example temporal extents above."
  (into {}
        (for [[example-name temporal] temporal-extents]
          [example-name (assoc example-base :TemporalExtents [temporal])])))

(defn xml-round-trip
  "Returns record after being converted to XML and back to UMM through
  the given to-xml and to-umm mappings."
  [record format]
  (core/parse-metadata :collection format (core/generate-metadata :collection format record)))

(deftest roundtrip-gen-parse
  (doseq [[example-name record] example-records]
    (testing example-name
      (are2 [metadata-format]
            (= (expected-conversion/convert record metadata-format)
               (xml-round-trip record metadata-format))

            "echo10"
            :echo10

            "dif9"
            :dif

            "dif10"
            :dif10

            "iso-smap"
            :iso-smap

            "ISO19115-2"
            :iso19115))))

(deftest generate-valid-xml
  (testing "valid XML is generated for each format"
    (doseq [[example-name record] example-records]
      (testing example-name
        (are [fmt]
             (empty? (core/validate-xml :collection fmt (core/generate-metadata :collection fmt record)))
             :echo10
             :dif
             :dif10
             :iso-smap
             :iso19115)))))

(defspec roundtrip-generator-gen-parse 100
  (for-all [umm-record umm-gen/umm-c-generator
            metadata-format (gen/elements [:echo10 :dif :dif10 :iso-smap :iso19115])]
    ;; TODO: right now, the TemporalExtents roundtrip conversion does not work with the generator
    ;; generated umm record. We exclude it from the comparison for now. This should be addressed
    ;; within CMR-1933.
    (is (= (dissoc (expected-conversion/convert umm-record metadata-format) :TemporalExtents)
           (dissoc (xml-round-trip umm-record metadata-format) :TemporalExtents)))))


