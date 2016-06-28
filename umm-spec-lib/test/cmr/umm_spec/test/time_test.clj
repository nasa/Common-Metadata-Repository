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
    :RangeDateTimes (mapv umm-cmn/map->RangeDateTimeType
                          [{:BeginningDateTime (t/date-time 2000)}
                           {:BeginningDateTime (t/date-time 2002)
                            :EndingDateTime (t/date-time 2003)}])
    :SingleDateTimes [(t/date-time 2003) (t/date-time 2004)]
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
                               :PeriodCycleDurationValue 3}])}))

(deftest test-temporal-all-dates
  (is (= (temporal-all-dates temporal)
         #{(t/date-time 2000)
           nil
           (t/date-time 2001)
           (t/date-time 2002)
           (t/date-time 2003)
           (t/date-time 2004)})))

(def example-record
  (umm-c/map->UMM-C
   {:TemporalExtents [temporal]}))

(deftest test-collection-start-date
  (testing "Multiple values"
   (is (= (t/date-time 2000)
          (collection-start-date example-record))))

  (testing "Single value"
    (is (= (t/date-time 2004)
           (collection-start-date {:TemporalExtents [{:SingleDateTimes [(t/date-time 2004)]}]}))))

  (testing "No dates"
    (is (nil? (collection-start-date {})))))

(deftest test-collection-end-date
  (testing "Nil ending-date"
    (is (= :present ; first range date does not have an end date
           (collection-end-date example-record))))
  (testing "Ends at present flag"
    (is (= :present ; The collection ends at present flag is set.
           (collection-end-date {:TemporalExtents [{:RangeDateTimes [{:BeginningDateTime (t/date-time 2000)
                                                                      :EndingDateTime (t/date-time 2001)}]
                                                    :EndsAtPresentFlag true}]}))))

  (testing "Single value"
    (is (= (t/date-time 2006)
           (collection-end-date {:TemporalExtents [{:SingleDateTimes [(t/date-time 2006)]}]}))))
  (testing "No dates"
    (is (nil? (collection-end-date {})))))


