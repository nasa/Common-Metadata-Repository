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

(def example-base
  "This contains an base example record with fields supported by all formats."
  (umm-c/map->UMM-C
    {:Platforms [(umm-cmn/map->PlatformType
                  {:ShortName "Platform 1"
                   :LongName "Example Platform Long Name 1"
                   ;; TODO This is a valid DIF 10 type; replace it with something that can't be
                   ;; round-tripped and handle that in expected-conversion.
                   :Type "Aircraft"
                   ;; :Instruments [(umm-cmn/map->InstrumentType {:ShortName "Instrument"})]
                   })]
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
