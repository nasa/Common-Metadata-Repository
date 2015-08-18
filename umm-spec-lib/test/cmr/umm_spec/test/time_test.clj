(ns cmr.umm-spec.test.time-test
  (:require [clj-time.core :as t]
            [clojure.test :refer :all]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as umm-cmn]
            [cmr.umm-spec.time :refer :all]))

(def temporal
  (umm-cmn/map->TemporalExtentType
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
                              :PeriodCycleDurationValue 3}])}))

(deftest test-temporal-all-dates
  (is (= (temporal-all-dates temporal)
         #{(t/date-time 2000)
           (t/date-time 2001)
           (t/date-time 2002)
           (t/date-time 2003)
           (t/date-time 2004)})))

(def example-record
  (umm-c/map->UMM-C
   {:TemporalExtent [temporal]}))

(deftest test-collection-start-date
  (is (= (collection-start-date example-record)
         (t/date-time 2000))))

(deftest test-collection-end-date
  (is (= (collection-end-date example-record)
         (t/date-time 2004))))
