(ns cmr.umm.test.temporal-coverage
  "Test construction of temporal coverage"
  (:require [clojure.test :refer :all]
            [cmr.umm.collection :as c]
            [cmr.umm.temporal-coverage :as tc]
            [cmr.common.date-time-parser :as p]))

(deftest validate-temporal-map
  (testing "empty map is valid"
    (tc/validate-temporal-map {}))
  (testing "map with one of the temproal date times is valid"
    (tc/validate-temporal-map {:range-date-times []})
    (tc/validate-temporal-map {:single-date-times []})
    (tc/validate-temporal-map {:periodic-date-times []}))
  (testing "map with more than one of the temproal date times is invalid"
    (is (thrown? Exception (tc/validate-temporal-map {:range-date-times [] :single-date-times []})))
    (is (thrown? Exception (tc/validate-temporal-map {:range-date-times [] :periodic-date-times []})))
    (is (thrown? Exception (tc/validate-temporal-map {:single-date-times [] :periodic-date-times []})))))

(deftest temporal-coverage
  (testing "construct temporal coverage with the correct default datetimes"
    (let [temporal-map {:time-type "Universal Time"
                        :range-date-times [(p/string->datetime "2010-01-05T05:30:30.550-05:00")]}
          expected (c/map->TemporalCoverage
                     (merge {:single-date-times [] :periodic-date-times []} temporal-map))]
      (is (= expected (tc/temporal-coverage temporal-map))))))