(ns cmr.umm.test.collection.temporal
  "Test construction of temporal coverage"
  (:require [clojure.test :refer :all]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.collection.temporal :as tc]
            [cmr.common.date-time-parser :as p]))

(deftest validate-temporal-map
  (testing "empty map is valid"
    (tc/validate-temporal-map {}))
  (testing "map with empty temporal values is valid"
    (tc/validate-temporal-map {:range-date-times []
                               :single-date-times []
                               :periodic-date-times []}))
  (testing "map with one of the temproal date times is valid"
    (tc/validate-temporal-map {:range-date-times ["dummy"]})
    (tc/validate-temporal-map {:single-date-times ["dummy"]})
    (tc/validate-temporal-map {:periodic-date-times ["dummy"]}))
  (testing "map with more than one of the temproal date times is invalid"
    (is (thrown-with-msg?
          Exception
          #"Only one of range-date-times, single-date-times and periodic-date-times can be provided."
          (tc/validate-temporal-map {:range-date-times ["dummy"] :single-date-times ["dummy"]})))
    (is (thrown-with-msg?
          Exception
          #"Only one of range-date-times, single-date-times and periodic-date-times can be provided."
          (tc/validate-temporal-map {:range-date-times ["dummy"] :periodic-date-times ["dummy"]})))
    (is (thrown-with-msg?
          Exception
          #"Only one of range-date-times, single-date-times and periodic-date-times can be provided."
          (tc/validate-temporal-map {:single-date-times ["dummy"] :periodic-date-times ["dummy"]})))))

(deftest temporal
  (testing "construct temporal coverage with the correct default datetimes"
    (let [temporal-map {:time-type "Universal Time"
                        :range-date-times [(p/parse-datetime "2010-01-05T05:30:30.550-05:00")]}
          expected (c/map->Temporal
                     (merge {:single-date-times [] :periodic-date-times []} temporal-map))]
      (is (= expected (tc/temporal temporal-map))))))
