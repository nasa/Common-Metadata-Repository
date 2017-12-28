(ns cmr.umm-spec.test.validation.temporal-extent
  "This has tests for UMM temporal extent validations."
  (:require
   [clj-time.core :as time]
   [clojure.test :refer :all]
   [cmr.common.time-keeper :as time-keeper]
   [cmr.common.util :as util :refer [are3]]
   [cmr.umm-spec.models.umm-collection-models :as coll]
   [cmr.umm-spec.models.umm-common-models :as c]
   [cmr.umm-spec.test.validation.umm-spec-validation-test-helpers :as h]))

(deftest collection-temporal-validation
  (testing "valid temporal"
    (let [r1 (h/range-date-time "1999-12-30T19:00:00Z" "1999-12-30T19:00:01Z")
          r2 (h/range-date-time "1999-12-30T19:00:00Z" nil)
          r3 (h/range-date-time "1999-12-30T19:00:00Z" "1999-12-30T19:00:00Z")
          s1 (c/map->TemporalExtentType {:SingleDateTimes [(time/date-time 2014 12 1)]})]
      (h/assert-valid (h/coll-with-range-date-times [[r1]]))
      (h/assert-valid (h/coll-with-range-date-times [[r2]]))
      (h/assert-valid (h/coll-with-range-date-times [[r3]]))
      (h/assert-valid (h/coll-with-range-date-times [[r1] [r2]]))
      (h/assert-valid (h/coll-with-range-date-times [[r1 r2] [r3]]))
      (h/assert-valid (h/coll-with-range-date-times [[r1 r2 r3]]))
      (h/assert-valid (h/coll-with-range-date-times [[r1]] true)) ; EndsAtPresentFlag = true
      (h/assert-warnings-valid (h/coll-with-range-date-times [[r1 r2] [r3]]))
      (h/assert-warnings-valid (coll/map->UMM-C {:TemporalExtents [s1]}))))

  (testing "invalid temporal"
    (testing "single error"
      (let [r1 (h/range-date-time "1999-12-30T19:00:02Z" "1999-12-30T19:00:01Z")
            r2 (h/range-date-time "1999-12-30T19:00:00Z" "1999-12-30T19:00:00Z")]
        (h/assert-invalid
         (h/coll-with-range-date-times [[r1]])
         [:TemporalExtents 0 :RangeDateTimes 0]
         ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"])
        (h/assert-invalid
         (h/coll-with-range-date-times [[r2] [r1]])
         [:TemporalExtents 1 :RangeDateTimes 0]
         ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"])))

    (testing "multiple errors"
      (let [r1 (h/range-date-time "1999-12-30T19:00:02Z" "1999-12-30T19:00:01Z")
            r2 (h/range-date-time "2000-12-30T19:00:02Z" "2000-12-30T19:00:01Z")]
        (h/assert-multiple-invalid
         (h/coll-with-range-date-times [[r1 r2]])
         [{:path [:TemporalExtents 0 :RangeDateTimes 0],
           :errors
           ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"]}
          {:path [:TemporalExtents 0 :RangeDateTimes 1],
           :errors
           ["BeginningDateTime [2000-12-30T19:00:02.000Z] must be no later than EndingDateTime [2000-12-30T19:00:01.000Z]"]}])
        (h/assert-multiple-invalid
         (h/coll-with-range-date-times [[r1] [r2]])
         [{:path [:TemporalExtents 0 :RangeDateTimes 0],
           :errors
           ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"]}
          {:path [:TemporalExtents 1 :RangeDateTimes 0],
           :errors
           ["BeginningDateTime [2000-12-30T19:00:02.000Z] must be no later than EndingDateTime [2000-12-30T19:00:01.000Z]"]}])))

    (testing "dates in past"
      (time-keeper/set-time-override! (time/date-time 2017 8 1))
      (testing "range date times"
        (let [r1 (h/range-date-time "2020-12-30T19:00:02Z" "2021-12-30T19:00:01Z")]
          (h/assert-warnings-multiple-invalid
           (h/coll-with-range-date-times [[r1]])
           [{:path [:TemporalExtents 0 :RangeDateTimes 0 :BeginningDateTime],
             :errors
             ["Date should be in the past."]}
            {:path [:TemporalExtents 0 :RangeDateTimes 0 :EndingDateTime],
             :errors
             [(str "Ending date should be in the past. Either set ending date to a date "
                   "in the past or remove end date and set the ends at present flag to true.")]}])))
      (testing "single date time"
        (let [c1 (c/map->TemporalExtentType {:SingleDateTimes [(time/date-time 2014 12 1)
                                                               (time/date-time 2020 12 30)]})]
          (h/assert-warnings-invalid
            (coll/map->UMM-C {:TemporalExtents [c1]})
            [:TemporalExtents 0 :SingleDateTimes 1]
            ["Date should be in the past."]))))))

(deftest ends-at-present-validation
  (let [r1 (h/range-date-time "1999-12-30T19:00:00Z" "2000-12-30T19:00:01Z")
        r2 (h/range-date-time "2001-12-30T19:00:00Z" nil)
        r3 (h/range-date-time "2003-12-30T19:00:00Z" "2005-12-30T19:00:00Z")]

    (testing "valid ends at present configuration"
      (are3 [range-date-times ends-at-present?]
        (h/assert-warnings-valid (h/coll-with-range-date-times range-date-times ends-at-present?))

        "ends at present true with nil end date"
        [[r2]] true

        "multiple ranges and ends at present true"
        [[r1 r2]] true

        "ends at present nil with nil end date"
        [[r1 r2]] nil))

   (testing "invalid ends at present configuration"
     (h/assert-warnings-invalid
       (h/coll-with-range-date-times [[r1 r3]] true)
       [:TemporalExtents 0]
       ["Ends at present flag is set to true, but an ending date is specified. Remove the latest ending date or set the ends at present flag to false."]))))
