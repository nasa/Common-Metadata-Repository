(ns cmr.spatial.test.line-string
  (:require [clojure.test :refer :all]
            [cmr.common.test.test-check-ext :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]

            ;; my code
            [cmr.spatial.math :refer :all]
            [cmr.spatial.point :as p]
            [cmr.spatial.line-string :as l]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.test.generators :as sgen]
            [cmr.spatial.validation :as v]
            [cmr.spatial.points-validation-helpers :as pv]
            [cmr.spatial.messages :as msg]))

(deftest line-string-validation
  (testing "geodetic line string"
    (testing "valid line string"
      (is (nil? (seq (v/validate (l/ords->line-string :geodetic [1 1, 2 2, 3 3]))))))
    (testing "invalid line strings"
      (are [ords error]
           (= [error]
              (v/validate (l/ords->line-string :geodetic ords)))
           [0 0, 1 1, 2 2, 1 1] (msg/duplicate-points [[1 (p/point 1 1)] [3 (p/point 1 1)]])
           [0 0, 1 1, 1 1] (msg/duplicate-points [[1 (p/point 1 1)] [2 (p/point 1 1)]])
           [0 0, 180 0] (msg/consecutive-antipodal-points [0 (p/point 0 0)] [1 (p/point 180 0)])
           [0 0, 181 0] (msg/shape-point-invalid 1 (msg/point-lon-invalid 181)))))
  (testing "cartesian line string"
    (testing "valid line string"
      (testing "normal"
        (is (nil? (seq (v/validate (l/ords->line-string :cartesian [1 1, 2 2, 3 3]))))))
      (testing "consecutive antipodal points, intersecting poles, and very long"
        (is (nil? (seq (v/validate (l/ords->line-string :cartesian [180 90, -180 90, 0 90, 0 -90, 10 -90])))))))
    (testing "invalid line strings"
      (are [ords error]
           (= [error]
              (v/validate (l/ords->line-string :cartesian ords)))
           [0 0, 1 1, 1 1] (msg/duplicate-points [[1 (p/point 1 1)] [2 (p/point 1 1)]])
           [0 0, 181 0] (msg/shape-point-invalid 1 (msg/point-lon-invalid 181))))))

