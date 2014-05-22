(ns cmr.spatial.test.ring
  (:require [clojure.test :refer :all]
            [cmr.common.test.test-check-ext :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]

            ;; my code
            [cmr.spatial.math :refer :all]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.ring :as r]
            [cmr.spatial.derived :as d]
            [cmr.spatial.test.generators :as sgen]))

;; TODO Need to finish this spec
#_(defspec ring-covers-point-spec num-tests

    ;; properties to verify
    ; The MBR covers all the ring points
    ; The ring covers all the points of the ring.
    ; The ring does not cover the external points.
    ; Midpoint of each arc of the ring is covered
    ; does or does not cover poles depending on the contains variable

    ;; other points inside the ring?
    ;; One way would be to take cross section arcs from one side of the mbr to another.
    ;; Find intersections of the arc and the ring.
    ;; All cross section arcs should intersect the ring 0 or an even number of times.
    ;; If an arc intersects at all then the midpoint of the intersections should be inside the ring.

  )

(declare ring-examples)

(deftest ring-examples-test
  (doseq [[ring-name
           {:keys [ords bounds internal-points external-points north-pole south-pole]}]
          ring-examples
          :let [north-pole (if (nil? north-pole) false north-pole)
                south-pole (if (nil? south-pole) false south-pole)]]
    (testing (str "Ring Example " ring-name)
      (let [ring (apply r/ords->ring ords)
            ring (d/calculate-derived ring)]
        (is (= bounds (:mbr ring)))

        (is (= north-pole (:contains-north-pole ring)))
        (is (= south-pole (:contains-south-pole ring)))

        (doseq [point (apply p/ords->points internal-points)]
            (is (r/covers-point? ring point) (str "Ring should cover point " point)))

        (doseq [point (apply p/ords->points external-points)]
            (is (not (r/covers-point? ring point)) (str "Ring should not cover point " point)))

        (is (r/intersects-ring? ring ring) "Ring should intersect itself")))))


(def ring-examples
  {:touching-eq {:ords [0,0 4,0 6,5 2,5 0,0]
                 :bounds (m/mbr 0 5.0030322578503075 6 0)
                 :internal-points [3,0.001 0.36,0.19 2.23,4.84 2,2]
                 :external-points [4.4,0.34 2.03,-0.27 0.37,1.83 3.55,5.05]}

   :convex {:ords [0,0 4,0 4,1 2,1 2,4 4,4 4,5 0,5 0,0]
            :bounds (m/mbr 0 5.0030322578503075 4 0)
            :internal-points [2.98,0.43 3.01,4.47]
            :external-points [4,2 4,3 3,2]}

   :around-np {:ords [8.09,84.55 175,80 -67.93,87.41 8.09,84.55]
               :north-pole true
               :bounds (m/mbr -180 90 180 80)
               :internal-points [173.94,84.33 -178.33,84.36 0,87.47]
               :external-points [0,80 92.58,88.97 174.58,78.85]}

   :around-sp {:ords [0,-85 -99.02,-85.42 179.01,-85.86 75,-85 0,-85]
               :south-pole true
               :bounds (m/mbr -180 -85 180 -90)
               :internal-points [-97.67,-86.36 162.06,-87.37]
               :external-points [-116.73,-86.24 19.62,-85.61]}

   :point-on-np {:ords [0,0 90,0 0,90 0,0]
                 :north-pole true
                 :bounds (m/mbr -180 90 180 0)
                 :internal-points [0,90 45,85 1,85 89.9,85 45,1 1,1 90,1]
                 :external-points [0,-90 -1,0 45,-85]}

   :point-on-np2 {:ords [-90,85 0,85 90,85 0,90 -90,85]
                  :north-pole true
                  :bounds (m/mbr -180 90 180 85)
                  :internal-points [0,87 89,87 -89,87]
                  :external-points [0,-90 180,0 0,0 -180,89 -135,89 135,89 180,89 89,-87 -89,-87]}

   :point-on-np-across-am {:ords [90,85 180,85 -90,85 0,90 90,85]
                           :north-pole true
                           :bounds (m/mbr -180 90 180 85)
                           :internal-points [-180,89 -135,89 135,89 180,89]
                           :external-points [0,-90 180,0 0,0 0,87 89,87 -89,87 89,-87 -89,-87]}

   :point-near-np {:ords [0,0 89.9,0 0,89.9 0,0]
                   :bounds (m/mbr 0 89.9 89.9 0)
                   :internal-points [0,89.9 45,85 1,85 86.95,85.01 45,1 1,1 89.85,1]
                   :external-points [0,90 0,-90 -1,0 45,-85]}

   :arc-across-np {:ords [-90,75, 0,60, 90,-74, -90,75]
                   :north-pole true
                   :bounds (m/mbr -180 90 180 -74)

                   :internal-points [0,90 -87,76 88,84 87,75 87,-72]
                   :external-points [0,-90 0,0 91,-72]}

   :point-on-sp {:ords [-175,0 175,0 0,-90 -175,0]
                 :south-pole true
                 :bounds (m/mbr -180 0 180 -90)
                 :internal-points [180,0 -180,0 180,-1 175,-10 -175,-10 175,-89 -175,-89]
                 :external-points [0,0 0,90 174,-10 -174,-10 174,-89 -174,-89]}

   :point-on-sp2 {:ords [-90,-85 0,-90 90,-85 0,-85 -90,-85]
                  :south-pole true
                  :bounds (m/mbr -180 -85 180 -90)
                  :internal-points [0,-87 89,-87 -89,-87]
                  :external-points [0,90 180,0 0,0 -180,-89 -135,-89 135,-89 180,-89 89,87 -89,87]}

   :point-near-sp {:ords [-175,0 175,0 180,-89 -175,0]
                   :bounds (m/mbr 175 0 -175 -89)
                   :internal-points [180,0 -180,0 180,-1 176,-10 -176,-10 179.99,-88.9 -179.99,-88.9]
                   :external-points [0,0 0,90 174,-10 -174,-10 174,-89 -174,-89]}

   :arc-across-sp {:ords [-90,75 90,-76 0,60 -90,75]
                   :south-pole true
                   :bounds (m/mbr -180 76.33917886681955 180 -90)
                   :internal-points [0,0 -81,75 0,-89 -89,-89 90,-76.1]
                   :external-points [0,90 180,0 -180,0 81,75 180,-89 90,-75.9]}

   :giant-zig-zag {:ords [-43.37,-45.69 27.42,12.19 -53.07,-52.64 44.94,30.89 -46.34,-44.43
                          26.09,12.69 -43.37,-45.69]
                   :bounds (m/mbr -53.07 30.89 44.94 -52.64)
                   :internal-points [8.9,-5.62 10.44,-6.52 -40.49,-42.55 13.05,-7.91]
                   :external-points [0,-90 0,0 9.57,-6.31 11.57,-7.19 -40.61,-43.93 -43.64,-45.96]}

   :on-eq {:ords [0,0 90,0 180,0 -90,0 0,0]
           :north-pole true
           :bounds (m/mbr -180 90 180 0)
           :internal-points [0,1]
           :external-points [0,-1]}

   :wrapping-around-np {:ords [-69.11,89.76 -60.39,89.66 54.98,89.53 127.49,89.34 -128.78,89.04
                               -66.7,89.02 52.17,88.82 127.05,88.52 -132.94,88.15 -62.27,87.98
                               38.97,87.99 115.94,88.08 120.61,88.27 47.01,88.36 -66.91,88.47
                               -131.53,88.54 128.99,88.98 55.21,89.16 -70.93,89.33 -123.07,89.37
                               136.34,89.67 58.88,89.75 -69.11,89.76]
                        :bounds (m/mbr -180 89.89262938556303 180 87.98)
                        :internal-points [-26.46,89.83 -18.28,88.81 127.85,88.73]
                        :external-points [-115.16,89.47 -95.54,88.95 -11.63,88.6 0,0 -180,0 90,0 -90,0]}

   :across-am {:ords [178.31,1.01 178.16,-3.85 -177.21,-4.19 -176.79,1.32 178.31,1.01]
               :bounds (m/mbr 178.16 1.32 -176.79 -4.19)
               :internal-points [-180,0 180,0 -178.46,-3.2 -180,-3.2]
               :external-points [180,2 -180,2 -90,0 90,0 0,0 179.48,-4.41]}

   :ends-on-am1 {:ords [172,0 174,-5 -180,-5 180,0.75 172,0]
                 :bounds (m/mbr 172 0.75 180 -5.006826875103258)
                 :internal-points [179.48,-4.41 180,-4 -180,-4]
                 :external-points [180,-7 180,7]}

   :ends-on-am2 {:ords [172,0 174,-5 -180,-5 -180,0.75 172,0]
                 :bounds (m/mbr 172 0.75 180 -5.006826875103258)
                 :internal-points [179.48,-4.41 180,-4 -180,-4]
                 :external-points [180,-7 180,7]}

   :ends-on-am3 {:ords [172,0 174,-5 180,-5 180,0.75 172,0]
                 :bounds (m/mbr 172 0.75 180 -5.006826875103258)
                 :internal-points [179.48,-4.41 180,-4 -180,-4]
                 :external-points [180,-7 180,7]}

   :starts-on-am1 {:ords [-176.17,0.46 180,0.75 -180,-5 -177.38,-3.92 -176.17,0.46]
                   :bounds (m/mbr -180 0.75 -176.17 -5)}

   :starts-on-am2 {:ords [-176.17,0.46, -180,0.75, -180,-5, -177.38,-3.92, -176.17,0.46]
                   :bounds (m/mbr -180 0.75 -176.17 -5)}

   :starts-on-am3 {:ords [-176.17,0.46 180,0.75 180,-5 -177.38,-3.92 -176.17,0.46]
                   :bounds (m/mbr -180 0.75 -176.17 -5)}})


