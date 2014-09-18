(ns cmr.spatial.test.orbits.orbit-geometry
  (:require [clojure.test :refer :all]
            [cmr.spatial.math :refer :all]
            [cmr.spatial.orbits.orbit-geometry :as o]
            [cmr.spatial.orbits.echo-orbits :as echo-orbits]))

(defn between?
  [low high v]
  (within-range? v low high))

(defn close-to?
  [n v]
  (approx= v n))

(deftest ground-track-test
  (testing "ground track returns correct latitude"
    (testing "returns correct latitude for retrograde and prograde orbit"
      (doseq [orbit-num [-2 -1 0 1 2]
              inclination-angle [98.0 82.0]]
        (let [orbit-params {:inclination-angle inclination-angle
                            :period 97.87
                            :swath-width 390.0
                            :start-circular-latitude -90.0
                            :number-of-orbits 1.0}]
          (are [offset checker]
               (let [result (o/ground-track orbit-params -71.2686 (+ (* orbit-num 97.87) offset))
                     lat (:lat result)]
                 (checker lat))
               14.4675 (partial between? 0 82)
               24.4675 (partial close-to? 82)
               34.4675 (partial between? 0 82)
               48.935 (partial close-to? 0)
               58.935 (partial between? -82 0)
               73.4025 (partial close-to? -82)
               83.4025 (partial between? -82 0)
               97.87 (partial close-to? 0)))))

    (testing "returns correct latitude for equitorial orbit"
      (doseq [orbit-num [-2 -1 0 1 2]]
        (let [orbit-params {:inclination-angle 0.0
                            :period 97.87
                            :swath-width 390.0
                            :start-circular-latitude -90.0
                            :number-of-orbits 1.0}]
          (are [offset]
               (let [result (o/ground-track orbit-params -71.2686 (+ (* orbit-num 97.87) offset))
                     lat (:lat result)]
                 (close-to? 0 lat))
               14.4675
               24.4675
               34.4675
               48.935
               58.935
               73.4025
               83.4025
               97.87)))))
  (testing "ground track returns correct longitude"
    (testing "returns correct longitude for retrograde orbit"
      (let [orbit-params {:inclination-angle 98.15
                          :period 98.88
                          :swath-width 1450.0
                          :start-circular-latitude -90.0
                          :number-of-orbits 0.5}]
        (is (close-to? 9.54 (:lon (o/ground-track orbit-params -158.1 (/ 98.88 2.0)))))
        (is (close-to? 177.18 (:lon (o/ground-track orbit-params -158.1 98.88))))
        (is (close-to? 152.47 (:lon (o/ground-track orbit-params 177.19 98.88))))
        (is (close-to? -39.93 (:lon (o/ground-track orbit-params 152.43 (/ 98.88 2.0)))))))
    (testing "returns longitude for prograde orbit"
      (let [orbit-params1 {:inclination-angle 98.15
                           :period 98.88
                           :swath-width 1450.0
                           :start-circular-latitude -90.0
                           :number-of-orbits 0.5}
            orbit-params2 {:inclination-angle 81.85
                           :period 98.88
                           :swath-width 1450.0
                           :start-circular-latitude -90.0
                           :number-of-orbits 0.5}]

        (is (close-to? (:lon (o/ground-track orbit-params1 -158.1, (/ 98.88 2.0)))
                       (:lon (o/ground-track orbit-params2 -158.1, (/ 98.88 2.0)))))

        (is (> (:lon (o/ground-track orbit-params2 -158.1, (/ 98.88 10.0)))
               (:lon (o/ground-track orbit-params2 -158.1, 0))))

        (is (not= (:lon (o/ground-track orbit-params1 -158.1, (/ 98.88 4.0)))
                  (:lon (o/ground-track orbit-params2 -158.1, (/ 98.88 4.0)))))

        (is (close-to? (:lon (o/ground-track orbit-params1 -158.1, 98.88))
                       (:lon (o/ground-track orbit-params2 -158.1, 98.88))))))

    (testing "returns correct longitude for polar orbit"
      (let [orbit-params {:inclination-angle 90.0
                          :period 90.0
                          :swath-width 2.0
                          :start-circular-latitude -50.0
                          :number-of-orbits 0.25}]
        (are [time-elapsed-mins expected-lon]
             (approx= expected-lon
                      (:lon (o/ground-track orbit-params 0 time-elapsed-mins))
                      0.01)
             10.0 (* -1.0 (mod (* (/ 360.0 echo-orbits/SOLAR_DAY_S) 10.0 60.0) 360.0))
             16.0 (* -1.0 (mod (* (/ 360.0 echo-orbits/SOLAR_DAY_S) 16.0 60.0) 360.0))
             22.5 -5.625
             23.0 (- 180.0 (mod (* (/ 360.0 echo-orbits/SOLAR_DAY_S) 23.0 60.0) 360.0))
             30.0 (- 180.0 (mod (* (/ 360.0 echo-orbits/SOLAR_DAY_S) 30.0 60.0) 360.0))
             67.5 -16.875)))))
