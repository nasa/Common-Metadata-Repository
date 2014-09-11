(ns cmr.search.test.validators.temporal
  "Contains tests for validating temporal condition"
  (:require [clojure.test :refer :all]
            [cmr.search.validators.validation :as v]
            [cmr.search.validators.temporal]
            [cmr.search.models.query :as q]
            [cmr.search.services.parameters.converters.temporal :as c]))

(deftest validate-temporal-start-day-test
  (testing "start-day-must-be-with-start-date"
    (is (= ["temporal_start_day must be accompanied by a temporal_start"]
           (v/validate
             (q/map->TemporalCondition {:start-day "1"}))))))

(deftest validate-temporal-end-day-test
  (testing "end-day-must-be-with-end-date"
    (is (= ["temporal_end_day must be accompanied by a temporal_end"]
           (v/validate
             (q/map->TemporalCondition {:end-day "1"}))))))
