(ns cmr.search.test.validators.temporal
  "Contains tests for validating temporal condition"
  (:require [clojure.test :refer :all]
            [cmr.search.validators.validation :as v]
            [cmr.search.validators.temporal]
            [cmr.search.services.parameter-converters.temporal :as c]))

(deftest validate-temporal-start-day-test
  (testing "start-day-must-be-with-start-date"
    (is (= ["temporal_start_day must be accompanied by a temporal_start"]
           (v/validate
             (c/map->temporal-condition {:start-day "1"})))))
  (testing "valid-start-day"
    (are [start-day] (empty? (v/validate
                               (c/map->temporal-condition {:start-day start-day
                                                          :start-date "2014-04-05T18:45:51Z"})))
         "1"
         "366"
         "10"))
  (testing "invalid-start-day"
    (are [start-day err-msg] (= [err-msg]
                                (v/validate
                                  (c/map->temporal-condition {:start-day start-day
                                                             :start-date "2014-04-05T18:45:51Z"})))
         "x" "temporal_start_day [x] must be an integer between 1 and 366"
         "0" "temporal_start_day [0] must be an integer between 1 and 366"
         "367" "temporal_start_day [367] must be an integer between 1 and 366")))

(deftest validate-temporal-end-day-test
  (testing "end-day-must-be-with-end-date"
    (is (= ["temporal_end_day must be accompanied by a temporal_end"]
           (v/validate
             (c/map->temporal-condition {:end-day "1"})))))
  (testing "valid-end-day"
    (are [end-day] (empty? (v/validate
                             (c/map->temporal-condition {:end-day end-day
                                                        :end-date "2014-04-05T18:45:51Z"})))
         "1"
         "366"
         "10"))
  (testing "invalid-end-day"
    (are [end-day err-msg] (= [err-msg]
                              (v/validate
                                (c/map->temporal-condition {:end-day end-day
                                                           :end-date "2014-04-05T18:45:51Z"})))
         "x" "temporal_end_day [x] must be an integer between 1 and 366"
         "0" "temporal_end_day [0] must be an integer between 1 and 366"
         "367" "temporal_end_day [367] must be an integer between 1 and 366")))
