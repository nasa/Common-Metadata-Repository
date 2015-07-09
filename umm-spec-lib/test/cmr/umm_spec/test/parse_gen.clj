(ns cmr.umm-spec.test.parse-gen
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.parse-gen :as p]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as umm-cmn]
            [cmr.umm-spec.json-schema :as js]
            [clj-time.core :as t]))

(def example-record
  (umm-c/map->UMM-C
    {
     :EntryId (umm-cmn/map->EntryIdType
                {:Id "short_V1"
                 ;; TODO instroduce these when testing ISO
                 ; :Version
                 ; :Authority
                 })
     :Product (umm-c/map->ProductType {:ShortName "short" :VersionId "V1"})
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

(comment

  (p/cleanup-schema
    (p/get-to-umm-mappings
      (js/load-schema-for-parsing "umm-c-json-schema.json")
      (p/load-mappings p/echo10-mappings)))

  (let [mappings (p/load-mappings p/echo10-mappings)]
    (println (p/generate-xml mappings example-record)))

  (:to-xml (p/load-mappings p/echo10-mappings))

  (require '[cmr.umm-spec.simple-xpath :as sx])


  )

(deftest roundtrip-gen-parse
  (let [mappings (p/load-mappings p/echo10-mappings)
        xml (p/generate-xml mappings example-record)
        umm-c-schema (js/load-schema-for-parsing "umm-c-json-schema.json")
        umm-mappings (p/get-to-umm-mappings umm-c-schema mappings)
        ; _ (cmr.common.dev.capture-reveal/capture umm-mappings mappings)
        parsed (p/parse-xml umm-mappings xml)]
    (is (= example-record parsed))))