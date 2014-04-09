(ns cmr.search.test.validators.date-range
  "Contains tests for validating date-range condition"
  (:require [clojure.test :refer :all]
            [cmr.search.validators.validation :as v]
            [cmr.search.validators.date-range]
            [cmr.search.models.query :as q]))

(deftest validate-start-date-test
  (testing "valid-start-date"
    (is (empty? (v/validate
                  (q/map->DateRangeCondition {:start-date "2014-04-05T00:00:00Z"})))))
  (testing "invalid-start-date"
    (are [start-date]
         (let [error (v/validate (q/map->DateRangeCondition {:start-date start-date}))]
           (is (= 1 (count error)))
           (re-find (re-pattern "temporal date is invalid:") (first error)))
         "2014-04-05T00:00:00"
         "2014-13-05T00:00:00Z"
         "2014-04-00T00:00:00Z"
         "2014-04-05T24:00:00Z"
         "2014-04-05T00:60:00Z"
         "2014-04-05T00:00:60Z")))

(deftest validate-end-date-test
  (testing "valid-end-date"
    (is (empty? (v/validate
                  (q/map->DateRangeCondition {:end-date "2014-04-05T00:00:00Z"})))))
  (testing "invalid-end-date"
    (are [end-date]
         (let [error (v/validate (q/map->DateRangeCondition {:end-date end-date}))]
           (is (= 1 (count error)))
           (re-find (re-pattern "temporal date is invalid:") (first error)))
         "2014-04-05T00:00:00"
         "2014-13-05T00:00:00Z"
         "2014-04-00T00:00:00Z"
         "2014-04-05T24:00:00Z"
         "2014-04-05T00:60:00Z"
         "2014-04-05T00:00:60Z")))

(deftest validate-start-date-before-end-date-test
  (testing "valid-start-end-date"
    (is (empty? (v/validate
                  (q/map->DateRangeCondition {:start-date "2014-04-04T00:00:00Z"
                                             :end-date "2014-04-05T00:00:00Z"})))))
  (testing "invalid-start-end-date"
    (is (= ["start_date [2014-04-06T00:00:00Z] must be before end_date [2014-04-05T00:00:00Z]"]
           (v/validate
             (q/map->DateRangeCondition {:start-date "2014-04-06T00:00:00Z"
                                        :end-date "2014-04-05T00:00:00Z"}))))))
