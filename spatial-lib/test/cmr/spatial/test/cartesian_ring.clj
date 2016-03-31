(ns cmr.spatial.test.cartesian-ring
  (:require [clojure.test :refer :all]
            [cmr.common.test.test-check-ext :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]

            ;; my code
            [cmr.spatial.math :refer :all]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.cartesian-ring :as cr]
            [cmr.spatial.ring-validations]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.derived :as d]
            [cmr.spatial.test.generators :as sgen]
            [cmr.spatial.validation :as v]
            [cmr.spatial.messages :as msg]
            [cmr.common.util :as u]))

(deftest ring-winding-test
  (testing "clockwise"
    (is (= :clockwise
           (cr/ring->winding
             (d/calculate-derived (rr/ords->ring :cartesian [4 3, 4 9, 9 9, 9 3, 4 3]))))))
  (testing "counter clockwise"
    (is (= :counter-clockwise
           (cr/ring->winding
             (d/calculate-derived (rr/ords->ring :cartesian [4 3, 9 3, 9 9, 4 9, 4 3])))))))

(deftest ring-validation-test
  (testing "valid ring"
    (testing "normal ring"
      (is (nil? (seq (v/validate (rr/ords->ring :cartesian [0 0, 1 0, 0 1, 0 0]))))))
    (testing "whole world"
      (is (nil? (seq (v/validate (rr/ords->ring :cartesian [-180 90, -180 -90, 180 -90, 180 90, -180 90]))))))
    (testing "points on opposite sides of the antimeridian"
      (is (nil? (seq (v/validate (rr/ords->ring :cartesian [0 -70, -180.0 -70, 180.0 -90.0, 180.0 -70, 0.0 -70])))))))
  (testing "invalid rings"
    (u/are2
      [ords msgs]
      (is (= (seq msgs)
             (seq (v/validate (rr/ords->ring :cartesian ords)))))

      "invalid point"
      [0 0, 181 0, 0 1, 0 0]
      [(msg/shape-point-invalid 1 (msg/point-lon-invalid 181))]

      "multiple invalid points and point parts"
      [0 0, 181 91, 0 92, 0 0]
      [(msg/shape-point-invalid 1 (msg/point-lon-invalid 181))
       (msg/shape-point-invalid 1 (msg/point-lat-invalid 91))
       (msg/shape-point-invalid 2 (msg/point-lat-invalid 92))]

      "Ring not closed"
      [0 0, 1 0, 0 1]
      [(msg/ring-not-closed)]

      "Duplicate points"
      [0 0, 1 0, 1 0, 0 1, 0 0]
      [(msg/duplicate-points [[1 (p/point 1 0)] [2 (p/point 1 0)]])]

      "duplicate non consecutive points"
      [0 0, 1 0, 4 5, 1 0, 0 1, 0 0]
      [(msg/duplicate-points [[1 (p/point 1 0)] [3 (p/point 1 0)]])]

      "Multiple duplicate points"
      [0 0, 1 0, 4 5, 1 0, 0 0, 4 5 0 1, 0 0]
      [(msg/duplicate-points [[0 (p/point 0 0)] [4 (p/point 0 0)]])
       (msg/duplicate-points [[1 (p/point 1 0)] [3 (p/point 1 0)]])
       (msg/duplicate-points [[2 (p/point 4 5)] [5 (p/point 4 5)]])]

      "very very close points"
      [0 0, 1 1, 1 1.000000001, 0 1, 0 0]
      [(msg/duplicate-points [[1 (p/point 1 1)] [2 (p/point 1 1.000000001)]])]

      "Not too close"
      [0 0, 1 1, 1 1.0000001, 0 1, 0 0]
      []

      "Self intersection"
      [4 3, 9 9, 4 9, 9 3, 4 3]
      [(msg/ring-self-intersections [(p/point 6.500000000000001 6.000000000000001)])]

      "Points in the wrong order"
      [4 3, 4 9, 9 9, 9 3, 4 3]
      [(msg/ring-points-out-of-order)])))

