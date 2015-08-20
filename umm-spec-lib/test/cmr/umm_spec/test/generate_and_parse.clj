(ns cmr.umm-spec.test.generate-and-parse
  "Tests roundtrip XML generation from a Clojure record and parsing it. Ensures that the same data
  is returned."
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.test.expected-conversion :as expected-conversion]
            [cmr.umm-spec.core :as core]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as umm-cmn]
            [clj-time.core :as t]
            [cmr.common.util :refer [are2]]))

(def example-record
  "This contains an example record with fields supported by all formats"
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
    
    :EntryId "short_V1"
    :EntryTitle "The entry title V5"
    :Version "V5"
    :Abstract "A very abstract collection"
    :DataLanguage "English"}))

(def temporal-extents
  "A sequence of possible values for UMM TemporalExtent."
  [(umm-cmn/map->TemporalExtentType
                         {:TemporalRangeType "temp range"
                          :PrecisionOfSeconds 3
                          :EndsAtPresentFlag false
                          :RangeDateTimes (mapv umm-cmn/map->RangeDateTimeType
                                                [{:BeginningDateTime (t/date-time 2000)
                                                  :EndingDateTime (t/date-time 2001)}
                                                 {:BeginningDateTime (t/date-time 2002)
                                                  :EndingDateTime (t/date-time 2003)}])})
                       (umm-cmn/map->TemporalExtentType
                         {:TemporalRangeType "temp range"
                          :PrecisionOfSeconds 3
                          :EndsAtPresentFlag false
                          :SingleDateTimes [(t/date-time 2003) (t/date-time 2004)]})
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
                                                     :PeriodCycleDurationValue 3}])})])

(def temporal-variations
  "A seq of example records for each of the values in the TemporalExtent in the base example-record
  above."
  (for [temporal temporal-extents]
    (if temporal
      (assoc example-record :TemporalExtents [temporal])
      example-record)))

(defn xml-round-trip
  "Returns record after being converted to XML and back to UMM through
  the given to-xml and to-umm mappings."
  [record format]
  (core/parse-metadata :collection format (core/generate-metadata :collection format record)))

(deftest roundtrip-gen-parse
  (doseq [record temporal-variations]
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
      :iso19115)))

(deftest generate-valid-xml
  (testing "valid XML is generated for each format"
    (doseq [record temporal-variations]
      (are [fmt]
          (empty? (core/validate-xml :collection fmt (core/generate-metadata :collection fmt record)))
        :echo10
        :dif
        :dif10
        :iso-smap
        :iso19115))))
