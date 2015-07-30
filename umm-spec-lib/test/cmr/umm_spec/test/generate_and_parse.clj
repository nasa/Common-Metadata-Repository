(ns cmr.umm-spec.test.generate-and-parse
  "Tests roundtrip XML generation from a Clojure record and parsing it. Ensures that the same data
  is returned."
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.xml-generation :as xg]
            [cmr.umm-spec.xml-mappings :as xm]
            [cmr.umm-spec.xml-parsing :as xp]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as umm-cmn]
            [cmr.umm-spec.json-schema :as js]
            [clj-time.core :as t]))

(def example-record
  (umm-c/map->UMM-C
    {
     :EntryId (umm-cmn/map->EntryIdType
                {:Id "short_V1"
                 ;; introduce these when testing ISO
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
                                                  :PeriodCycleDurationValue 3}])
                         })]}))

(comment

  (xm/cleanup-schema
    (xm/get-to-umm-mappings
      (js/load-schema-for-parsing "umm-c-json-schema.json")
      (xm/load-mappings xm/echo10-mappings)))

  (println (xg/generate-xml xm/echo10-to-xml example-record))

  (require '[cmr.umm-spec.simple-xpath :as sx])


  )

(deftest roundtrip-gen-parse
  (let [xml (xg/generate-xml xm/umm-to-echo10-xml example-record)
        parsed (xp/parse-xml xm/echo10-xml-to-umm xml)]
    (is (= example-record parsed))))