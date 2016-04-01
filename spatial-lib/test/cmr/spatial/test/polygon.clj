(ns cmr.spatial.test.polygon
  (:require [clojure.test :refer :all]
            [cmr.common.test.test-check-ext :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]

            ;; my code
            [cmr.spatial.math :refer :all]
            [cmr.spatial.point :as p]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.geodetic-ring :as gr]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.derived :as d]
            [cmr.spatial.test.generators :as sgen]
            [cmr.spatial.validation :as v]
            [cmr.spatial.messages :as msg]))

(deftest polygon-validation
  (testing "geodetic polygons"
    (testing "simple valid polygon"
      (testing "simple"
        (is (nil? (seq (v/validate (poly/polygon :geodetic [(rr/ords->ring :geodetic [0 0, 1 0, 0 1, 0 0])])))))))
    (testing "invalid polygon boundary"
      (is (= [(msg/shape-point-invalid 1 (msg/point-lon-invalid 181))]
             (v/validate (poly/polygon :geodetic [(rr/ords->ring :geodetic [0 0, 181 0, 0 1, 0 0])])))))
    (testing "with holes"
      (let [outer (rr/ords->ring :geodetic [-5.26,-2.59, 11.56,-2.77, 10.47,8.71, -5.86,8.63, -5.26,-2.59])
            hole1 (rr/ords->ring :geodetic [6.95,2.05, 2.98,2.06, 3.92,-0.08, 6.95,2.05])
            hole2 (rr/ords->ring :geodetic [5.18,6.92, -1.79,7.01, -2.65,5, 4.29,5.05, 5.18,6.92])
            hole-inside-hole2 (rr/ords->ring :geodetic [3.6,6.67 -0.60,6.66 -1.543,5.51 3.43,5.62 3.6,6.67])
            hole-across-hole2 (rr/ords->ring :geodetic [2.17,7.72 -0.53,7.55 -1.46,4.71 2.98,4.67 2.17,7.72])


            ;; Outer 2 is similar to outer 1 except it has a fifth point that stretches above hole2.
            ;; All of the points of hole2 are in outer2 but the sides intersect. This allows
            ;; testing that more than just point containment is done in hole validation.
            outer2 (rr/ords->ring :geodetic [1.37,7.45 11.56,-2.77 10.47,8.71 -5.86,8.63 -5.29,-2.85 1.37,7.45])]
        (testing "valid"
          (is (nil? (seq (v/validate (poly/polygon :geodetic [outer hole1 hole2]))))))
        (testing "hole with invalid point"
          (is (= [(msg/shape-point-invalid 1 (msg/point-lon-invalid 181))]
                 (v/validate (poly/polygon :geodetic [outer
                                                      (rr/ords->ring :geodetic [0 0, 181 0, 0 1, 0 0])])))))
        (testing "boundary inside hole"
          (is (= [(msg/hole-not-covered-by-boundary 0)]
                 (v/validate (poly/polygon :geodetic [hole1 outer])))))
        (testing "hole completely disjoint from boundary"
          (is (= [(msg/hole-not-covered-by-boundary 0)]
                 (v/validate (poly/polygon :geodetic [hole1 hole2])))))
        (testing "Multiple invalid holes"
          (is (= [(msg/hole-not-covered-by-boundary 0)
                  (msg/hole-not-covered-by-boundary 1)
                  (msg/hole-intersects-hole 0 1)]
                 (v/validate (poly/polygon :geodetic [hole1 hole2 outer])))))
        (testing "Boundary intersects hole"
          (is (= [(msg/hole-not-covered-by-boundary 0)]
                 (v/validate (poly/polygon :geodetic [outer2 hole2])))))
        (testing "holes intersect"
          (testing "hole inside hole"
            (is (= [(msg/hole-intersects-hole 0 1)]
                   (v/validate (poly/polygon :geodetic [outer hole2 hole-inside-hole2])))))
          (testing "hole intersects hole"
            (is (= [(msg/hole-intersects-hole 0 2)]
                   (v/validate (poly/polygon :geodetic [outer hole-across-hole2 hole1 hole2])))))))))
  (testing "cartesian polygons"
    (testing "simple valid polygon"
      (testing "simple"
        (is (nil? (seq (v/validate (poly/polygon :cartesian [(rr/ords->ring :cartesian [0 0, 1 0, 0 1, 0 0])])))))))
    (testing "invalid polygon boundary"
      (is (= [(msg/shape-point-invalid 1 (msg/point-lon-invalid 181))]
             (v/validate (poly/polygon :cartesian [(rr/ords->ring :cartesian [0 0, 181 0, 0 1, 0 0])])))))
    (testing "with holes"
      (let [outer (rr/ords->ring :cartesian [-5.26,-2.59, 11.56,-2.77, 10.47,8.71, -5.86,8.63, -5.26,-2.59])
            hole1 (rr/ords->ring :cartesian [6.95,2.05, 2.98,2.06, 3.92,-0.08, 6.95,2.05])
            hole2 (rr/ords->ring :cartesian [5.18,6.92, -1.79,7.01, -2.65,5, 4.29,5.05, 5.18,6.92])
            hole-inside-hole2 (rr/ords->ring :cartesian [3.6,6.67 -0.60,6.66 -1.543,5.51 3.43,5.62 3.6,6.67])
            hole-across-hole2 (rr/ords->ring :cartesian [2.17,7.72 -0.53,7.55 -1.46,4.71 2.98,4.67 2.17,7.72])

            ;; Outer 2 is similar to outer 1 except it has a fifth point that stretches above hole2.
            ;; All of the points of hole2 are in outer2 but the sides intersect. This allows
            ;; testing that more than just point containment is done in hole validation.
            outer2 (rr/ords->ring :cartesian [1.37,7.45 11.56,-2.77 10.47,8.71 -5.86,8.63 -5.29,-2.85 1.37,7.45])]
        (testing "valid"
          (is (nil? (seq (v/validate (poly/polygon :cartesian [outer hole1 hole2]))))))
        (testing "hole with invalid point"
          (is (= [(msg/shape-point-invalid 1 (msg/point-lon-invalid 181))]
                 (v/validate (poly/polygon :cartesian [outer
                                                       (rr/ords->ring :cartesian [0 0, 181 0, 0 1, 0 0])])))))
        (testing "boundary inside hole"
          (is (= [(msg/hole-not-covered-by-boundary 0)]
                 (v/validate (poly/polygon :cartesian [hole1 outer])))))
        (testing "hole completely disjoint from boundary"
          (is (= [(msg/hole-not-covered-by-boundary 0)]
                 (v/validate (poly/polygon :cartesian [hole1 hole2])))))
        (testing "Multiple invalid holes"
          (is (= [(msg/hole-not-covered-by-boundary 0)
                  (msg/hole-not-covered-by-boundary 1)
                  (msg/hole-intersects-hole 0 1)]
                 (v/validate (poly/polygon :cartesian [hole1 hole2 outer])))))
        (testing "Boundary intersects hole"
          (is (= [(msg/hole-not-covered-by-boundary 0)]
                 (v/validate (poly/polygon :cartesian [outer2 hole2])))))
        (testing "holes intersect"
          (testing "hole inside hole"
            (is (= [(msg/hole-intersects-hole 0 1)]
                   (v/validate (poly/polygon :cartesian [outer hole2 hole-inside-hole2])))))
          (testing "hole intersects hole"
            (is (= [(msg/hole-intersects-hole 0 2)]
                   (v/validate (poly/polygon :cartesian [outer hole-across-hole2 hole1 hole2]))))))))))

