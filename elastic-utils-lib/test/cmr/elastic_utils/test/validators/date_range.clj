(ns cmr.elastic-utils.test.validators.date-range
  "Contains tests for validating date-range condition"
  (:require [clojure.test :refer [deftest is testing]]
            [clj-time.core :as time]
            [cmr.elastic-utils.es-query-validation :as v]
            [cmr.common.services.search.query-model :as q]
            [cmr.elastic-utils.validators.date-range]))

(deftest validate-start-date-before-end-date-test
  (testing "valid-start-end-date"
    (is (empty? (v/validate
                  (q/map->DateRangeCondition {:start-date (time/date-time 1986 10 14 4 3 27)
                                              :end-date (time/date-time 1986 10 14 4 3 28)})))))
  (testing "invalid-start-end-date"
    (is (= ["start_date [2014-04-06T00:00:00Z] must be before end_date [2014-04-05T00:00:00Z]"]
           (v/validate
             (q/map->DateRangeCondition {:start-date (time/date-time 2014 4 6)
                                         :end-date (time/date-time 2014 4 5)}))))))
