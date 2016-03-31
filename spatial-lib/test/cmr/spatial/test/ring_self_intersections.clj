(ns cmr.spatial.test.ring-self-intersections
  "Tests the function for determing if a ring has self intersections"
  (:require [clojure.test :refer :all]
            [cmr.common.test.test-check-ext :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]

            ;; my code
            [cmr.spatial.math :refer :all]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.geodetic-ring :as gr]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.derived :as d]
            [cmr.spatial.test.generators :as sgen]))

(declare ring-examples)

(deftest ring-self-intersections-test
  (doseq [[ring-name {:keys [ords expected-intersects]}] ring-examples]
    (let [ring (d/calculate-derived (rr/ords->ring :geodetic ords))
          self-intersections (rr/self-intersections ring)]
      (if expected-intersects
        (let [sorter (fn [{:keys [lon lat]}]
                       [lon lat])
              self-intersections (distinct (sort-by sorter (map (partial p/round-point 4)
                                                                self-intersections)))
              expected-intersects (sort-by sorter (p/ords->points expected-intersects))]
          (is (approx= expected-intersects self-intersections 0.001)
              (format "%s should have approximate self intersections" (name ring-name))))
        (is (empty? self-intersections)
            (format "%s should have no self intersections" (name ring-name)))))))


(def ring-examples
  {:on-eq {:ords [0,0, 4,0, 6,5, 2,5, 0,0]}

   :intersected-on-eq {:ords [0,0, 4,0, 6,5, 7,0, 0,0]
                       :expected-intersects [4,0]}

   :criss-cross {:ords [0,0, 5.63,-1.89, 2.18,5.28, 5.79,1.44, 0,0]
                 :expected-intersects [4.218, 1.050]}

   :around-sp {:ords [0,-85, 75,-85, 179.01,-85.86, -99.02,-85.42, 0,-85]}

   :around-sp2 {:ords [0,-85, 75,-85, -62,-88.42, -166.19,-87.77, 179.01,-85.86, -153.5,-86.66,
                       -99.02,-85.42, 0,-85]}

   :around-sp2-intersects {:ords [0,-85, 75,-85, -49.68,-86.89, -166.19,-87.77,179.01,-85.86,
                                  -153.5,-86.66, -99.02,-85.42, 0,-85]
                           :expected-intersects [-49.697,-86.892, -49.656, -86.892]}

   :around-np {:ords [8.09,84.55, -67.93,87.41, 175,80, 8.09,84.55]}

   :around-np2 {:ords [17.12,86.62, -45.66,87.6, -160.24,87.53, 164.04,86.47,
                       -168.51,88.09,169.92,88.72, -23.28,88.13, 141.99,88.12, 32.67,88.08,
                       17.12,86.62]}

   :around-np-with-intersects {:ords [17.12,86.62, -45.66,87.6, -160.24,87.53, 164.04,86.47,
                                      -168.51,88.09, 65.79,89.38, -53.04,89.25, 141.99,88.12,
                                      32.67,88.08, 17.12,86.62]
                               :expected-intersects [152.985,89.468]}

   :expected-intersects-on-np {:ords [90,85, 135,85, 0,90, -135,85, -90,85, 90,85]
                               :expected-intersects [0,90]}

   :expected-intersects-on-np2 {:ords [90,75, 175,75, -175,90, -175,75, -90,75, 90,75]
                                :expected-intersects [0,90]}

   :crosses-am {:ords [-155.14,-87.85, -156.92,-86.22, 162.63,-86.82, -155.14,-87.85]}

   :crosses-am-intersects-both-sides {:ords [-149.84,-87.66, -164.96,-86.3, 157.48,-86.11,
                                             148.01,-87.57, 146.55,-86.39, -146.9,-86.42,
                                             -149.84,-87.66]
                                      :expected-intersects [154.859,-86.674, -160.906,-86.822]}

   :starts-on-am {:ords [180,0, -178,-5, -174,-5, -176,0, 180,0]}

   :starts-on-am2 {:ords [-180,0, -178,-5, -174,-5, -176,0, -180,0]}

   :ends-on-am {:ords [172,0, 174,-5, -180,-5, 176,0, 172,0]}

   :ends-on-am2 {:ords [172,0, 174,-5, 180,-5, 176,0, 172,0]}

   :tiny-1 {:ords [0,0, 0.00004,0, 0.00006,0.00005, 0.00002,0.00005, 0,0]}

   :tiny-2 {:ords [-64.35882,79.83329, -64.05444,79.83329, -64.05444,79.83325, -64.35882,79.83325,
                   -64.35882,79.83329]}

   :looping-with-no-intersections {:ords [1.33,3.07, 1.94,2.84, 2.05,3.41, 0.78,3.61, 0.46,2.72,
                                          2.45,2.11, 2.71,3.9, 0.17,4.27, -0.26,2.07, 2.79,1.45,
                                          3.02,3.89, 2.73,1.89, 0.12,2.41,0.43,3.98, 2.32,3.65,
                                          2.22,2.53, 0.89,2.79, 0.83,3.27, 1.78,3.32, 1.8,3.03,
                                          1.33,3.07]}

   :looping-with-many-intersections {:ords [1.33,3.07, 1.94,2.84, 2.05,3.41, 0.78,3.61, 0.46,2.72,
                                            2.45,2.11, 2.71,3.9, 0.17,4.27, -0.26,2.07, 2.79,1.45,
                                            -0.21,3.42, 3.07,4.03, 2.73,1.89, 0.12,2.41, 0.43,3.98,
                                            2.32,3.65, 2.22,2.53, 0.89,2.79, 0.83,3.27, 1.78,3.32,
                                            1.8,3.03,1.33,3.07]
                                     :expected-intersects [2.518,3.928, 1.65,3.768, 0.793,3.608,
                                                           0.778,3.605, 0.34,3.523, 0.012,3.462,
                                                           -0.021,3.296, 0.259,3.113,
                                                           0.536,2.931, 1.206,2.492,
                                                           1.856,2.065]}

   :intersect-on-ring-point {:ords [1,1, 1,5, 5,1, 5,5, 1,5, 1,1]
                             :expected-intersects [1,5]}

   :intersect-along-ring {:ords [1,1, 1,4, 5,1, 5,5, 1,5, 1,1]
                          :expected-intersects [1,4]}

   :ring-touches-arc {:ords [1,1, 2,1, 1,5, 2,7, 1,10, 1,1]
                      :expected-intersects [1,5]}})