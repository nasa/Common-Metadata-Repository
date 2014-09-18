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

(deftest along-track-swath-edges-test
  (are [inclination-angle time-elapsed-mins expected-lon-lat-pairs]
       (let [orbit-params {:inclination-angle inclination-angle
                           :period 100.0
                           :swath-width 1450.0
                           :start-circular-latitude -90.0
                           :number-of-orbits 0.5}]
         (approx= expected-lon-lat-pairs
                  (for [{:keys [lon lat]} (o/along-track-swath-edges orbit-params 50.0 time-elapsed-mins)]
                    [lon lat])))
         120.0 10 [[21.59486555375376, 26.675279834885025], [33.95780038981773, 34.23221060991104]]
         120.0 45 [[-126.32263579537941, 12.06822925257768], [-137.91178853855175, 18.825748958188626]]
         120.0 90 [[41.04219961018231, -34.23221060991105], [53.40513444624621, -26.675279834885032]]
         120.0 240 [[-164.0948655537537, 26.675279834885114], [-176.45780038981763, 34.23221060991113]]

         75 10 [[50.44862206135383, 36.404800459259626], [65.48226107784352, 32.321873537431756]]
         75 45  [[-139.41443077410403, 19.023771495399235], [-152.573071473028, 15.494691505035474]]
         75 90 [[9.517738922156468, -32.321873537431756], [24.55137793864629, -36.40480045925964]]
         75 240 [[167.0513779386462, 36.40480045925973], [152.01773892215647, 32.32187353743186]]

         90 10 [[39.454495742055784, 35.73091301641356], [55.545504257944216, 35.73091301641356]]
         90 45 [[-134.3936609198613, 17.879496860815472], [-148.1063390801387, 17.879496860815472]]
         90 90 [[19.454495742055823, -35.730913016413574], [35.545504257944216, -35.730913016413574]]
         90 240 [[178.04550425794432, 35.730913016413666], [161.9544957420557, 35.730913016413666]]

         0 10 [[83.49999999999999, 6.523732106725029], [83.49999999999999, -6.523732106725029]]
         0 45 [[-159.25, 6.523732106725029], [-159.25, -6.523732106725029]]
         0 90 [[-8.49999999999998, 6.523732106725029], [-8.49999999999998, -6.523732106725029]]
         0 240 [[133.99999999999991, 6.523732106725029], [133.99999999999991, -6.523732106725029]]))


