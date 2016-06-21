(ns cmr.umm-spec.test.validation.collection
  "This has tests for UMM validations."
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.validation.core :as v]
            [cmr.umm-spec.models.collection :as c]
            [cmr.umm-spec.test.validation.helpers :as helpers]
            [cmr.common.services.errors :as e]))

(defn assert-valid
  "Asserts that the given collection is valid."
  [collection]
  (is (empty? (v/validate-collection collection))))

(defn assert-invalid
  "Asserts that the given umm model is invalid and has the expected error messages.
  field-path is the path within the metadata to the error. expected-errors is a list of string error
  messages."
  [collection field-path expected-errors]
  (is (= [(e/map->PathErrors {:path field-path
                              :errors expected-errors})]
         (v/validate-collection collection))))

(defn assert-multiple-invalid
  "Asserts there are multiple errors at different paths invalid with the UMM. Expected errors
  should be a list of maps with path and errors."
  [collection expected-errors]
  (is (= (set (map e/map->PathErrors expected-errors))
         (set (v/validate-collection collection)))))

(deftest collection-temporal-validation
  (testing "valid temporal"
    (let [r1 (helpers/range-date-time "1999-12-30T19:00:00Z" "1999-12-30T19:00:01Z")
          r2 (helpers/range-date-time "1999-12-30T19:00:00Z" nil)
          r3 (helpers/range-date-time "1999-12-30T19:00:00Z" "1999-12-30T19:00:00Z")]
      (assert-valid (helpers/coll-with-range-date-times [[r1]]))
      (assert-valid (helpers/coll-with-range-date-times [[r2]]))
      (assert-valid (helpers/coll-with-range-date-times [[r3]]))
      (assert-valid (helpers/coll-with-range-date-times [[r1] [r2]]))
      (assert-valid (helpers/coll-with-range-date-times [[r1 r2] [r3]]))
      (assert-valid (helpers/coll-with-range-date-times [[r1 r2 r3]]))
      (assert-valid (helpers/coll-with-range-date-times [[r1]] true)))) ; EndsAtPresentFlag = true

 (testing "invalid temporal"
    (testing "single error"
      (let [r1 (helpers/range-date-time "1999-12-30T19:00:02Z" "1999-12-30T19:00:01Z")
            r2 (helpers/range-date-time "1999-12-30T19:00:00Z" "1999-12-30T19:00:00Z")]
        (assert-invalid
          (helpers/coll-with-range-date-times [[r1]])
          [:TemporalExtents 0 :RangeDateTimes 0]
          ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"])
        (assert-invalid
          (helpers/coll-with-range-date-times [[r2] [r1]])
          [:TemporalExtents 1 :RangeDateTimes 0]
          ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"])))

    (testing "multiple errors"
      (let [r1 (helpers/range-date-time "1999-12-30T19:00:02Z" "1999-12-30T19:00:01Z")
            r2 (helpers/range-date-time "2000-12-30T19:00:02Z" "2000-12-30T19:00:01Z")]
        (assert-multiple-invalid
          (helpers/coll-with-range-date-times [[r1 r2]])
          [{:path [:TemporalExtents 0 :RangeDateTimes 0],
            :errors
            ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"]}
           {:path [:TemporalExtents 0 :RangeDateTimes 1],
            :errors
            ["BeginningDateTime [2000-12-30T19:00:02.000Z] must be no later than EndingDateTime [2000-12-30T19:00:01.000Z]"]}])
       (assert-multiple-invalid
         (helpers/coll-with-range-date-times [[r1] [r2]])
         [{:path [:TemporalExtents 0 :RangeDateTimes 0],
           :errors
           ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"]}
          {:path [:TemporalExtents 1 :RangeDateTimes 0],
           :errors
           ["BeginningDateTime [2000-12-30T19:00:02.000Z] must be no later than EndingDateTime [2000-12-30T19:00:01.000Z]"]}])))))
