(ns cmr.search.test.services.parameter-converters.two-d-coordinate-system
  (:require [clojure.test :refer :all]
            [cmr.search.services.parameters.converters.two-d-coordinate-system :as t]
            [cmr.search.models.query :as qm]))

(deftest two-d-param-str->condition-test
  (testing "two d param string to condition"
    (are [s two-d-name condition] (= (t/two-d-param-str->condition :granule s)
                                     (qm/map->TwoDCoordinateSystemCondition
                                       {:two-d-name two-d-name
                                        :two-d-conditions condition}))
         "wrs-1" "wrs-1" nil
         "wrs-1:5,10" "wrs-1"
         [(qm/map->TwoDCoordinateCondition
            {:coordinate-1-cond (qm/->CoordinateValueCondition 5.0)
             :coordinate-2-cond (qm/->CoordinateValueCondition 10.0)})]

         "wrs-1:8-10,0-10" "wrs-1"
         [(qm/map->TwoDCoordinateCondition
            {:coordinate-1-cond (qm/->CoordinateRangeCondition 8.0 10.0)
             :coordinate-2-cond (qm/->CoordinateRangeCondition 0.0 10.0)})]

         "wrs-1:5,10:8-10,0-10" "wrs-1"
         [(qm/map->TwoDCoordinateCondition
            {:coordinate-1-cond (qm/->CoordinateValueCondition 5.0)
             :coordinate-2-cond (qm/->CoordinateValueCondition 10.0)})
          (qm/map->TwoDCoordinateCondition
            {:coordinate-1-cond (qm/->CoordinateRangeCondition 8.0 10.0)
             :coordinate-2-cond (qm/->CoordinateRangeCondition 0.0 10.0)})])))

(deftest string->coordinate-condition-test
  (testing "string to coordinate condition"
    (are [s condition] (= (t/string->coordinate-condition s) condition)
         "" nil
         " " nil
         "-" nil
         "5" (qm/->CoordinateValueCondition 5.0)
         "0-10" (qm/->CoordinateRangeCondition 0.0 10.0)
         "-10" (qm/->CoordinateRangeCondition nil 10.0)
         "0-" (qm/->CoordinateRangeCondition 0.0 nil))))
