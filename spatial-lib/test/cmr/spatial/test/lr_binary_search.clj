(ns cmr.spatial.test.lr-binary-search
  (:require [clojure.test :refer :all]
            [cmr.common.test.test-check-ext :as ext-gen :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]
            [clojure.math.combinatorics :as combo]
            [clojure.string :as str]

            ;; my code
            [cmr.spatial.math :refer :all]
            [cmr.spatial.point :as p]
            [cmr.spatial.arc :as a]
            [cmr.spatial.geodetic-ring :as gr]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.derived :as d]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.test.generators :as sgen]
            [cmr.spatial.lr-binary-search :as lbs]
            [cmr.spatial.relations :as relations])
  (:import cmr.spatial.point.Point))



(defspec all-rings-have-lrs {:times 100 :printer-fn sgen/print-failed-ring}
  (for-all [ring (gen/bind sgen/coordinate-system sgen/rings)]
    (let [lr (lbs/find-lr ring false)]
      (and lr
           (rr/covers-br? ring lr)))))

(defn polygon-has-valid-lr?
  "Returns true if the polygon has a valid lr."
  [polygon]
  (let [lr (lbs/find-lr polygon false)]
    (and lr
         (poly/covers-br? polygon lr))))

(defspec simple-geodetic-polygon-with-holes-has-lr {:times 100 :printer-fn sgen/print-failed-ring}
  (let [boundary (d/calculate-derived (rr/ords->ring :geodetic [0 0, 10 0, 10 10, 0 10, 0 0]))]
    (for-all [hole (sgen/rings-in-ring boundary)]
      (let [polygon (d/calculate-derived (poly/polygon :geodetic [boundary hole]))]
        (polygon-has-valid-lr? polygon)))))

(defspec simple-cartesian-polygon-with-holes-has-lr {:times 100 :printer-fn sgen/print-failed-ring}
  (let [boundary (d/calculate-derived (rr/ords->ring :cartesian [0 0, 10 0, 10 10, 0 10, 0 0]))]
    (for-all [hole (sgen/rings-in-ring boundary)]
      (let [polygon (d/calculate-derived (poly/polygon :cartesian [boundary hole]))]
        (polygon-has-valid-lr? polygon)))))

(defspec all-polygons-with-holes-have-lrs {:times 100 :printer-fn sgen/print-failed-polygon}
  (for-all [polygon (gen/no-shrink sgen/polygons-with-holes)]
    (polygon-has-valid-lr? polygon)))

(deftest example-polygons-have-lrs
  (let [polygons-ordses [[[-60 0 -60 1 -61 1 -61 0 -60 0]]
                         [[-60.25739 -68.00377, -59.64225 -68.05437, -59.57187 -68.05437,
                           -59.5413 -68.05092, -59.49545 -68.04172, -59.49851 -68.03482,
                           -59.51381 -68.03597, -59.59335 -68.04747, -59.93609 -68.01872,
                           -60.21455 -67.99917, -60.25739 -67.99917, -60.25739 -68.00377]]
                         [[-61.36033 58.05291, -58.37146 57.57561, -57.27939 59.11659,
                           -60.39083 59.61554, -61.36033 58.05291]]
                         [[-41.71891 65.44888, -41.75479 65.43968, -41.77687 65.43048,
                           -41.79895 65.41553, -41.82103 65.38678, -41.85415 65.31893,
                           -41.85415 65.30398, -41.84589 65.30398, -41.79067 65.40633,
                           -41.76859 65.42243, -41.71339 65.44198, -41.66923 65.44543,
                           41.62783 65.44083, -41.59471 65.43163, -41.49535 65.37413,
                           -41.48431 65.37528, -41.48707 65.38103, -41.56435 65.42703,
                           -41.58919 65.43968, -41.63335 65.45003, -41.71063 65.45003,
                           -41.71891 65.44888]]

                         ;; This one has a point that falls on the vertical arc used to find intersections
                         [[-41.71891 65.44888, -41.75479 65.43968, -41.77687 65.43048,
                           -41.79895 65.41553, -41.82103 65.38678, -41.85415 65.31893,
                           -41.85415 65.30398, -41.84589 65.30398, -41.79067 65.40633,
                           -41.76859 65.42243, -41.71339 65.44198, -41.66923 65.44543,
                           -41.62783 65.44083, -41.59471 65.43163, -41.49535 65.37413,
                           -41.48431 65.37528, -41.48707 65.38103, -41.56435 65.42703,
                           -41.58919 65.43968, -41.63335 65.45003, -41.71063 65.45003,
                           -41.71891 65.44888]]

                         ;; Has a point on the pole and a hole
                         [[-94.25 34.5 -81.25 90.0 58.25 43.8 81.33333333333333 -37.166666666666664
                           122.33333333333333 -3.75 173.75 33.0 -94.25 34.5]
                          [135.2 18.833333333333332 -154.85714285714286 73.66666666666667 -114.2
                           46.5 -145.0 73.33333333333333 155.66666666666666 70.8 112.75 11.5
                           135.2 18.833333333333332]]

                         ;; Very small
                         [[-60.005286,77.184739, -59.977307,77.186401, -59.983349,77.191409,
                           -60.011338,77.189746, -60.005286,77.184739]]]]
    (doseq [polygon-ordses polygons-ordses]
      (let [polygon (d/calculate-derived (poly/polygon :geodetic
                                                       (mapv (partial rr/ords->ring :geodetic)
                                                             polygon-ordses)))]
        (is (polygon-has-valid-lr? polygon))))))




