(ns cmr.common-app.test.services.search.validators.date-range
  "Contains tests for validating date-range condition"
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [cmr.common-app.services.search.query-validation :as v]
            [cmr.common-app.services.search.validators.date-range]
            [cmr.common-app.services.search.query-model :as q]))

(deftest validate-start-date-before-end-date-test
  (testing "valid-start-end-date"
    (is (empty? (v/validate
                  (q/map->DateRangeCondition {:start-date (t/date-time 1986 10 14 4 3 27)
                                              :end-date (t/date-time 1986 10 14 4 3 28)})))))
  (testing "invalid-start-end-date"
    (is (= ["start_date [2014-04-06T00:00:00Z] must be before end_date [2014-04-05T00:00:00Z]"]
           (v/validate
             (q/map->DateRangeCondition {:start-date (t/date-time 2014 4 6)
                                         :end-date (t/date-time 2014 4 5)}))))))
