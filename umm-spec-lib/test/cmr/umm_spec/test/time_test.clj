(ns cmr.umm-spec.test.time-test
  (:require
   [clj-time.core :as t]
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.umm-spec.models.umm-collection-models :as umm-c]
   [cmr.umm-spec.models.umm-common-models :as umm-cmn]
   [cmr.umm-spec.time :as time]))

(def temporal
  (umm-cmn/map->TemporalExtentType
   {:TemporalRangeType "temp range"
    :PrecisionOfSeconds 3
    :EndsAtPresentFlag false
    :RangeDateTimes (mapv umm-cmn/map->RangeDateTimeType
                          [{:BeginningDateTime (t/date-time 2000)}
                            ;; no ending date so this range ends at the present
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
  (is (= (time/temporal-all-dates temporal)
         #{(t/date-time 2000)
           :present ; no ending date for the first range
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
          (time/collection-start-date example-record))))

  (testing "Single value"
    (is (= (t/date-time 2004)
           (time/collection-start-date {:TemporalExtents [{:SingleDateTimes [(t/date-time 2004)]}]}))))

  (testing "No dates"
    (is (nil? (time/collection-start-date {})))))

(deftest test-collection-end-date
  (testing "Nil ending-date"
    (is (= :present ; first range date does not have an end date
           (time/collection-end-date example-record))))
  (testing "Ends at present flag"
    (is (= :present ; The collection ends at present flag is set.
           (time/collection-end-date {:TemporalExtents [{:RangeDateTimes [{:BeginningDateTime (t/date-time 2000)}]
                                                                      :EndingDateTime (t/date-time 2001)}]
                                                    :EndsAtPresentFlag true}))))

  (testing "Single value"
    (is (= (t/date-time 2006)
           (time/collection-end-date {:TemporalExtents [{:SingleDateTimes [(t/date-time 2006)]}]}))))
  (testing "No dates"
    (is (nil? (time/collection-end-date {})))))

(deftest normalize-range-end-dates
  (testing "Normalize end dates"
    (are3 [range-date-times ends-at-present expected-ranges]
      (is (= expected-ranges
             (#'time/normalize-temporal-ranges range-date-times ends-at-present)))

      "Ranges correct, ends at present false"
      [{:BeginningDateTime (t/date-time 2000)
        :EndingDateTime (t/date-time 2001)}
       {:BeginningDateTime (t/date-time 2003)
        :EndingDateTime (t/date-time 2005)}
       {:BeginningDateTime (t/date-time 1996)
        :EndingDateTime (t/date-time 1997)}]
      false
      [{:BeginningDateTime (t/date-time 2000)
        :EndingDateTime (t/date-time 2001)}
       {:BeginningDateTime (t/date-time 2003)
        :EndingDateTime (t/date-time 2005)}
       {:BeginningDateTime (t/date-time 1996)
        :EndingDateTime (t/date-time 1997)}]

     "Ranges correct, ends at present true"
     [{:BeginningDateTime (t/date-time 2000)
       :EndingDateTime (t/date-time 2001)}
      {:BeginningDateTime (t/date-time 2003)}
      {:BeginningDateTime (t/date-time 1996)
       :EndingDateTime (t/date-time 1997)}]
     true
     [{:BeginningDateTime (t/date-time 2000)
       :EndingDateTime (t/date-time 2001)}
      {:BeginningDateTime (t/date-time 2003)
       :EndingDateTime nil}
      {:BeginningDateTime (t/date-time 1996)
       :EndingDateTime (t/date-time 1997)}]

     "Has end date, ends at present true"
     [{:BeginningDateTime (t/date-time 2000)
       :EndingDateTime (t/date-time 2001)}
      {:BeginningDateTime (t/date-time 2003)
       :EndingDateTime (t/date-time 2005)}
      {:BeginningDateTime (t/date-time 1996)
       :EndingDateTime (t/date-time 1997)}]
     true
     [{:BeginningDateTime (t/date-time 2000)
       :EndingDateTime (t/date-time 2001)}
      {:BeginningDateTime (t/date-time 2003)
       :EndingDateTime nil}
      {:BeginningDateTime (t/date-time 1996)
       :EndingDateTime (t/date-time 1997)}]

     "Empty temporal ranges"
     [] true []

     "Nil temporal ranges"
     nil true []

     "One temporal range"
     [{:BeginningDateTime (t/date-time 2000)
       :EndingDateTime (t/date-time 2001)}]
     true
     [{:BeginningDateTime (t/date-time 2000)
       :EndingDateTime nil}])))


(deftest resolve-range-overlaps
  (testing "Resolve overlaps"
    (are3 [range-date-times expected-ranges]
      (is (= expected-ranges
             (#'time/resolve-range-overlaps range-date-times)))

      "No overlap"
      [{:BeginningDateTime (t/date-time 2000)
        :EndingDateTime (t/date-time 2001)}
       {:BeginningDateTime (t/date-time 2003)
        :EndingDateTime (t/date-time 2005)}
       {:BeginningDateTime (t/date-time 1996)
        :EndingDateTime (t/date-time 1997)}]
      [{:BeginningDateTime (t/date-time 1996)
        :EndingDateTime (t/date-time 1997)}
       {:BeginningDateTime (t/date-time 2000)
        :EndingDateTime (t/date-time 2001)}
       {:BeginningDateTime (t/date-time 2003)
        :EndingDateTime (t/date-time 2005)}]

      "2 ranges overlap"
      [{:BeginningDateTime (t/date-time 2000)
        :EndingDateTime (t/date-time 2002)}
       {:BeginningDateTime (t/date-time 2001)
        :EndingDateTime (t/date-time 2005)}
       {:BeginningDateTime (t/date-time 1996)
        :EndingDateTime (t/date-time 1997)}]
      [{:BeginningDateTime (t/date-time 2000)
        :EndingDateTime (t/date-time 2005)}
       {:BeginningDateTime (t/date-time 1996)
        :EndingDateTime (t/date-time 1997)}]

      "Range encompassed by other range"
      [{:BeginningDateTime (t/date-time 2000)
        :EndingDateTime (t/date-time 2005)}
       {:BeginningDateTime (t/date-time 2001)
        :EndingDateTime (t/date-time 2003)}
       {:BeginningDateTime (t/date-time 1996)
        :EndingDateTime (t/date-time 1997)}]
      [{:BeginningDateTime (t/date-time 2000)
        :EndingDateTime (t/date-time 2005)}
       {:BeginningDateTime (t/date-time 1996)
        :EndingDateTime (t/date-time 1997)}]

      "All ranges overlap"
      [{:BeginningDateTime (t/date-time 2000)
        :EndingDateTime (t/date-time 2002)}
       {:BeginningDateTime (t/date-time 2001)
        :EndingDateTime (t/date-time 2005)}
       {:BeginningDateTime (t/date-time 2004)
        :EndingDateTime (t/date-time 2007)}]
      [{:BeginningDateTime (t/date-time 2000)
        :EndingDateTime (t/date-time 2007)}]

      "Overlapping with nil end date"
      [{:BeginningDateTime (t/date-time 2004)
        :EndingDateTime nil}
       {:BeginningDateTime (t/date-time 2001)
        :EndingDateTime (t/date-time 2005)}
       {:BeginningDateTime (t/date-time 1996)
        :EndingDateTime (t/date-time 1997)}]
      [{:BeginningDateTime (t/date-time 2001)
        :EndingDateTime nil}
       {:BeginningDateTime (t/date-time 1996)
        :EndingDateTime (t/date-time 1997)}]

      "Empty temporal ranges"
      [] []

      "Nil temporal ranges"
      nil [])))
