(ns cmr.umm-spec.test.validation.collection
  "This has tests for UMM validations."
  (:require [clojure.test :refer :all]
            [cmr.common.date-time-parser :as dtp]
            [cmr.umm-spec.models.common :as c]
            [cmr.umm-spec.models.collection :as coll]
            [cmr.umm-spec.test.validation.helpers :as h]))

(defn- range-date-time
  "Returns a temporal range map given beginning and end date strings.

  Example: (range-date-time \"1999-12-30T19:00:00Z\" \"1999-12-30T19:00:01Z\")"
  [begin-date-time end-date-time]
  (let [begin-date-time (when begin-date-time (dtp/parse-datetime begin-date-time))
        end-date-time (when end-date-time (dtp/parse-datetime end-date-time))]
    (c/map->RangeDateTimeType
      {:BeginningDateTime begin-date-time
       :EndingDateTime end-date-time})))

(defn- coll-with-range-date-times
  "Returns a collection with the given temporal ranges."
  ([range-date-times]
   (coll-with-range-date-times range-date-times nil))
  ([range-date-times ends-at-present?]
   (coll/map->UMM-C
     {:TemporalExtents (map #(c/map->TemporalExtentType {:RangeDateTimes %
                                                         :EndsAtPresentFlag ends-at-present?})
                            range-date-times)
      :EntryTitle "et"})))

(deftest collection-temporal-validation
  (testing "valid temporal"
    (let [r1 (range-date-time "1999-12-30T19:00:00Z" "1999-12-30T19:00:01Z")
          r2 (range-date-time "1999-12-30T19:00:00Z" nil)
          r3 (range-date-time "1999-12-30T19:00:00Z" "1999-12-30T19:00:00Z")]
      (h/assert-valid (coll-with-range-date-times [[r1]]))
      (h/assert-valid (coll-with-range-date-times [[r2]]))
      (h/assert-valid (coll-with-range-date-times [[r3]]))
      (h/assert-valid (coll-with-range-date-times [[r1] [r2]]))
      (h/assert-valid (coll-with-range-date-times [[r1 r2] [r3]]))
      (h/assert-valid (coll-with-range-date-times [[r1 r2 r3]]))
      (h/assert-valid (coll-with-range-date-times [[r1]] true)))) ; EndsAtPresentFlag = true

  (testing "invalid temporal"
    (testing "single error"
      (let [r1 (range-date-time "1999-12-30T19:00:02Z" "1999-12-30T19:00:01Z")
            r2 (range-date-time "1999-12-30T19:00:00Z" "1999-12-30T19:00:00Z")]
        (h/assert-invalid
          (coll-with-range-date-times [[r1]])
          [:TemporalExtents 0 :RangeDateTimes 0]
          ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"])
        (h/assert-invalid
          (coll-with-range-date-times [[r2] [r1]])
          [:TemporalExtents 1 :RangeDateTimes 0]
          ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"])))

    (testing "multiple errors"
      (let [r1 (range-date-time "1999-12-30T19:00:02Z" "1999-12-30T19:00:01Z")
            r2 (range-date-time "2000-12-30T19:00:02Z" "2000-12-30T19:00:01Z")]
        (h/assert-multiple-invalid
          (coll-with-range-date-times [[r1 r2]])
          [{:path [:TemporalExtents 0 :RangeDateTimes 0],
            :errors
            ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"]}
           {:path [:TemporalExtents 0 :RangeDateTimes 1],
            :errors
            ["BeginningDateTime [2000-12-30T19:00:02.000Z] must be no later than EndingDateTime [2000-12-30T19:00:01.000Z]"]}])
        (h/assert-multiple-invalid
          (coll-with-range-date-times [[r1] [r2]])
          [{:path [:TemporalExtents 0 :RangeDateTimes 0],
            :errors
            ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"]}
           {:path [:TemporalExtents 1 :RangeDateTimes 0],
            :errors
            ["BeginningDateTime [2000-12-30T19:00:02.000Z] must be no later than EndingDateTime [2000-12-30T19:00:01.000Z]"]}])))))

(deftest collection-projects-validation
  (let [c1 (c/map->ProjectType {:ShortName "C1"})
        c2 (c/map->ProjectType {:ShortName "C2"})
        c3 (c/map->ProjectType {:ShortName "C3"})]
    (testing "valid projects"
      (h/assert-valid (coll/map->UMM-C {:Projects [c1 c2]})))

    (testing "invalid projects"
      (testing "duplicate names"
        (let [coll (coll/map->UMM-C {:Projects [c1 c1 c2 c2 c3]})]
          (h/assert-invalid
            coll
            [:Projects]
            ["Projects must be unique. This contains duplicates named [C1, C2]."]))))))
