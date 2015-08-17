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
   {:EntryTitle "The entry title V5"
    :EntryId (umm-cmn/map->EntryIdType {:Id "short_V1"})
    :Abstract "Abstract description"
    :TemporalExtent [(umm-cmn/map->TemporalExtentType
                      {:TemporalRangeType "temp range"
                       :PrecisionOfSeconds 3
                       :EndsAtPresentFlag false
                       :RangeDateTime (mapv umm-cmn/map->RangeDateTimeType
                                            [{:BeginningDateTime (t/date-time 2000)
                                              :EndingDateTime (t/date-time 2001)}
                                             {:BeginningDateTime (t/date-time 2002)
                                              :EndingDateTime (t/date-time 2003)}])})
                     (umm-cmn/map->TemporalExtentType
                      {:TemporalRangeType "temp range"
                       :PrecisionOfSeconds 3
                       :EndsAtPresentFlag false
                       :SingleDateTime [(t/date-time 2003) (t/date-time 2004)]})
                     (umm-cmn/map->TemporalExtentType
                      {:TemporalRangeType "temp range"
                       :PrecisionOfSeconds 3
                       :EndsAtPresentFlag false
                       :PeriodicDateTime (mapv umm-cmn/map->PeriodicDateTimeType
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
                                                 :PeriodCycleDurationValue 3}])})]}))

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

    ;; TODO
    "iso-smap"
    :iso-smap

    "ISO19115-2"
    :iso19115))

(deftest generate-valid-xml
  (testing "valid XML is generated for each format"
    (are [fmt]
        (empty? (core/validate-xml :collection fmt (core/generate-metadata :collection fmt example-record)))
      ;; TODO fix the invalid ECHO10 XML
      :echo10
      :dif
      :dif10
      :iso-smap
      :iso19115)))

(comment
  (->> example-record
       (core/generate-metadata :collection :dif)
       (core/parse-metadata :collection :dif))
  )
