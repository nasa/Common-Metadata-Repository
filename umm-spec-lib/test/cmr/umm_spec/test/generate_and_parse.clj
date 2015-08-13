(ns cmr.umm-spec.test.generate-and-parse
  "Tests roundtrip XML generation from a Clojure record and parsing it. Ensures that the same data
  is returned."
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.umm-to-xml-mappings.xml-generator :as xg]
            [cmr.umm-spec.umm-to-xml-mappings.echo10 :as xm-echo10]
            [cmr.umm-spec.xml-to-umm-mappings.echo10 :as um-echo10]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2 :as xm-iso2]
            [cmr.umm-spec.xml-to-umm-mappings.iso19115-2 :as um-iso2]
            [cmr.umm-spec.umm-to-xml-mappings.dif9 :as xm-dif9]
            [cmr.umm-spec.xml-to-umm-mappings.dif9 :as um-dif9]
            [cmr.umm-spec.umm-to-xml-mappings.dif10 :as xm-dif10]
            [cmr.umm-spec.xml-to-umm-mappings.dif10 :as um-dif10]
            [cmr.umm-spec.umm-to-xml-mappings.iso-smap :as xm-smap]
            [cmr.umm-spec.xml-to-umm-mappings.iso-smap :as um-smap]
            [cmr.umm-spec.xml-to-umm-mappings.parser :as xp]
            [cmr.umm-spec.test.expected-conversion :as expected-conversion]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as umm-cmn]
            [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.core :as c]
            [clj-time.core :as t]
            [cmr.common.util :refer [are2]]))

(def example-record-echo10-supported
  "This contains an example record will all the fields supported by ECHO10. It supported
  more initially for demonstration purposes."
  (umm-c/map->UMM-C
    {
     :EntryId (umm-cmn/map->EntryIdType {:Id "short_V1"})
     :EntryTitle "The entry title V5"
     :Abstract "A very abstract collection"
     :TemporalExtent [(umm-cmn/map->TemporalExtentType
                        {:TemporalRangeType "temp range"
                         :PrecisionOfSeconds 3
                         :EndsAtPresentFlag false
                         :RangeDateTime (mapv umm-cmn/map->RangeDateTimeType
                                              [{:BeginningDateTime (t/date-time 2000)
                                                :EndingDateTime (t/date-time 2001)}
                                               {:BeginningDateTime (t/date-time 2002)
                                                :EndingDateTime (t/date-time 2003)}])
                         :SingleDateTime [(t/date-time 2003) (t/date-time 2004)]
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

(def example-record
  "This contains an example record with fields supported by all formats"
  (umm-c/map->UMM-C
    {:EntryTitle "The entry title V5"
     :EntryId (umm-cmn/map->EntryIdType {:Id "short_V1"})
     :Abstract "Abstract description"}))


(deftest roundtrip-gen-parse
  (are2 [metadata-format to-xml to-umm]
        (let [xml (xg/generate-xml to-xml example-record)
              validation-errors (c/validate-xml :collection metadata-format xml)
              parsed (xp/parse-xml to-umm xml)
              expected-manip-fn (expected-conversion/metadata-format->expected-conversion metadata-format)
              expected (expected-manip-fn example-record)]
          (and (empty? validation-errors)
               (= expected parsed)))
        "echo10"
        :echo10 xm-echo10/umm-c-to-echo10-xml um-echo10/echo10-xml-to-umm-c

        "dif9"
        :dif xm-dif9/umm-c-to-dif9-xml um-dif9/dif9-xml-to-umm-c

        "dif10"
        :dif10 xm-dif10/umm-c-to-dif10-xml um-dif10/dif10-xml-to-umm-c

        "iso-smap"
        :iso-smap xm-smap/umm-c-to-iso-smap-xml um-smap/iso-smap-xml-to-umm-c

        "ISO19115-2"
        :iso19115 xm-iso2/umm-c-to-iso19115-2-xml um-iso2/iso19115-2-xml-to-umm-c)

  ;; This is here because echo10 supported additional fields
  (testing "echo10 supported fields"
    (let [xml (xg/generate-xml xm-echo10/umm-c-to-echo10-xml example-record-echo10-supported)
          parsed (xp/parse-xml um-echo10/echo10-xml-to-umm-c xml)
          expected-manip-fn (expected-conversion/metadata-format->expected-conversion :echo10)]
      (is (= (expected-manip-fn example-record-echo10-supported) parsed)))))
