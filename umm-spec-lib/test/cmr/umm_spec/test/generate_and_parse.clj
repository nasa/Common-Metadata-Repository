(ns cmr.umm-spec.test.generate-and-parse
  "Tests roundtrip XML generation from a Clojure record and parsing it. Ensures that the same data
  is returned."
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.xml-mappings.xml-generator :as xg]
            [cmr.umm-spec.xml-mappings.iso19115-2 :as xm-iso2]
            [cmr.umm-spec.xml-mappings.echo10 :as xm-echo10]
            [cmr.umm-spec.umm-mappings.iso19115-2 :as um-iso2]
            [cmr.umm-spec.umm-mappings.echo10 :as um-echo10]
            [cmr.umm-spec.umm-mappings.parser :as xp]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as umm-cmn]
            [cmr.umm-spec.json-schema :as js]
            [clj-time.core :as t]
            [cmr.common.util :as u :refer [are2]]))

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
                                                   :PeriodCycleDurationValue 3}])
                         })]}))

(def example-record
  "This contains an example record with fields supported by all formats"
  (umm-c/map->UMM-C
    {:EntryId (umm-cmn/map->EntryIdType {:Id "short_V1"})
     :EntryTitle "The entry title V5"}))

(defn expected-echo10
  "This manipulates the expected parsed UMM record based on lossy conversion in ECHO10."
  [expected]
  ;; ECHO10 returns entry id as a combination of short name and version. It generates short name
  ;; from entry id. So the expected entry id when going from umm->echo10->umm is the original
  ;; entry id concatenated with the version id.
  (update-in expected [:EntryId :Id] #(str % "_"
                                           ;; TODO put version here once it's added to UMM.
                                           )))

(deftest roundtrip-gen-parse
  (are2 [to-xml to-umm expected-manip-fn]
        (let [xml (xg/generate-xml to-xml example-record)
              parsed (xp/parse-xml to-umm xml)
              expected (expected-manip-fn example-record)]
          (is (= expected parsed)))
        "echo10"
        xm-echo10/umm-c-to-echo10-xml um-echo10/echo10-xml-to-umm-c expected-echo10

        "ISO19115-2"
        xm-iso2/umm-c-to-iso19115-2-xml um-iso2/iso19115-2-xml-to-umm-c identity
        )

  ;; This is here because echo10 supported additional fields
  (testing "echo10 supported fields"
    (let [xml (xg/generate-xml xm-echo10/umm-c-to-echo10-xml example-record-echo10-supported)
          parsed (xp/parse-xml um-echo10/echo10-xml-to-umm-c xml)]
      (is (= (expected-echo10 example-record-echo10-supported) parsed)))))



(comment

  (println
    (cmr.umm-spec.xml-mappings.iso19115-2/generate-xml
      cmr.umm-spec.xml-mappings.iso19115-2/umm-c-to-echo10-xml-with-new-dsl
      example-record-echo10-supported))

    (println
      (cmr.umm-spec.xml-mappings.iso19115-2/generate-xml
          cmr.umm-spec.xml-mappings.iso19115-2/umm-c-to-iso19115-2-xml-with-new-dsl
          example-record-echo10-supported))

  )













