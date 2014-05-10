(ns cmr.spatial.test.mbr
  (:require [clojure.test :refer :all]

            ; [clojure.test.check.clojure-test :refer [defspec]]
            ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
            [cmr.common.test.test-check-ext :refer [defspec]]

            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]

            ;; my code
            [cmr.spatial.math :refer :all]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.point :as p]
            [cmr.spatial.test.generators :as sgen]))

(deftest on-antimeridian
  (testing "west on antimeridian"
    (testing "east is west of prime meridian"
      (is (= -180.0 (:west (m/mbr -180 5 -175 -5))))
      (is (= -180.0 (:west (m/mbr 180 5 -175 -5)))))

    (testing "east is east of prime meridian"
      (is (= -180.0 (:west (m/mbr -180 5 175 -5))))
      (is (= -180.0 (:west (m/mbr 180 5 175 -5))))))

  (testing "east on antimeridian"
    (testing "west is west of prime meridian"
      (is (= 180.0 (:east (m/mbr -175 5 -180 -5))))
      (is (= 180.0 (:east (m/mbr -175 5 180 -5)))))

    (testing "west is east of prime meridian"
      (is (= 180.0 (:east (m/mbr 175 5 -180 -5))))
      (is (= 180.0 (:east (m/mbr 175 5 180 -5))))))

  (testing "both on antimeridian"
    (let [get-west-east #(vector (:west %) (:east %))]
      (testing "covers only the antimeridian"
        (is (= [-180.0 -180.0] (get-west-east (m/mbr -180 5 -180 -5))))
        (is (= [180.0 180.0] (get-west-east (m/mbr 180 5 180 -5)))))
      (testing "covers whole world"
        (is (= [-180.0 180.0] (get-west-east (m/mbr -180 5 180 -5))))
        (is (= [-180.0 180.0] (get-west-east (m/mbr 180 5 -180 -5))))))))

(deftest equality-with-doubles
  (is (= (m/mbr -10 5 10 -5) (m/mbr -10.0 5.0 10.0 -5.0))))

(deftest center-point-test
  (testing "normal"
    (is (= (p/point 1 10)
           (m/center-point (m/mbr -50 20 52 0)))))
  (testing "across antimeridian"
    (is (= (p/point 179 10)
           (m/center-point (m/mbr 177 20 -179 0)))))
  (testing "full width"
    (is (= (p/point 0 0)
           (m/center-point (m/mbr -180 90 180 -90))))))

(defspec covers-point-spec 100
  (for-all [mbr sgen/mbrs]
    (let [{w :west n :north e :east s :south} mbr
          corner-points (p/ords->points w,n e,n e,s w,s)
          midlon (if (m/crosses-antimeridian? mbr)
                   (let [dist (+ (- 180 w) (- e -180))
                         half (/ dist 2.0)
                         mid (+ half w)]
                     (if (> mid 180) (- mid 360) mid))
                   (avg [w e]))
          midpoint (p/point midlon (avg [n s]))]
      (and
        (every? #(m/covers-point? mbr %) corner-points)
        (m/covers-point? mbr midpoint)))))

(defspec union-test 100
  (for-all [mbr1 sgen/mbrs
            mbr2 sgen/mbrs]
    (let [unioned (m/union mbr1 mbr2)]
      (and
        ;; is commutative
        (= unioned (m/union mbr2 mbr1))

        (if (or (m/crosses-antimeridian? mbr1) (m/crosses-antimeridian? mbr2))
          ;; If either cross the antimeridian then the result should cross or it should be the whole world
          (or (m/crosses-antimeridian? unioned)
              (= [-180.0 180.0] [(:west unioned) (:east unioned)]))
          ;; otherwise we can't determine whether it will cross antimeridian or not.
          true)

        ;; should cover all parts
        (every? #(m/covers-lon? unioned %)
                (mapcat #(map (fn [k] (k %)) [:west :east]) [mbr1 mbr2]))

        (every? #(m/covers-lat? unioned %)
                (mapcat #(map (fn [k] (k %)) [:north :south]) [mbr1 mbr2]))
        ))))

(defspec union-self-test 100
  (for-all [mbr sgen/mbrs]
    (and
      ;; union with self = self
      (= mbr (m/union mbr mbr))

      ;; union with reverse area = whole world
      (let [lon-flipped (m/mbr (:east mbr) (:north mbr) (:west mbr) (:south mbr))]
        (= (m/mbr -180 (:north mbr) 180 (:south mbr)) (m/union mbr lon-flipped))))))


(deftest union-example-test
  (testing "should extend east to cover bounding rectangle to the east"
    (let [br1 (m/mbr -5 10 6 -1)
          br2 (m/mbr 9 10 12 -1)]
      (is (= (m/union br1 br2) (m/mbr -5 10 12 -1)))))

  (testing "should extend west to cover bounding rectangle to the east"
    (let [br1 (m/mbr -5 10 6 -1)
          br2 (m/mbr -70 10 -55 -1)]
      (is (= (m/union br1 br2) (m/mbr -70 10 6 -1)))))

  (testing "should extend north to cover bounding rectangle to the north"
    (let [br1 (m/mbr -5 10 6 -1)
          br2 (m/mbr -5 15 6 12)]
      (is (= (m/union br1 br2) (m/mbr -5 15 6 -1)))))

  (testing "should extend south to cover bounding rectangle to the north"
    (let [br1 (m/mbr -5 10 6 -1)
          br2 (m/mbr -5 -5 6 -12)]
      (is (= (m/union br1 br2) (m/mbr -5 10 6 -12)))))

  (testing "should expand shortest distance including over antimeridian"
    (let [br1 (m/mbr 170 10 175 -1)
          br2 (m/mbr -177 10 -173 -1)
          expected (m/mbr 170 10 -173 -1)
          unioned (m/union br1 br2)]
      (is (= unioned expected))
      (is (= unioned (m/union br2 br1)))
      (is (m/crosses-antimeridian? unioned))))

  (testing "should not expand across antimeridian if it is shorter not to do so"
    (let [br1 (m/mbr -69.11 10 54.98 -1)
          br2 (m/mbr 54.98 10 127.49 -1)
          expected (m/mbr -69.11 10 127.49 -1)]
      (is (= (m/union br1 br2) expected))))

  (testing "one crossing the antimeridian"

    (testing "should not have to expand if it's already covered"
      (let [br1 (m/mbr -1 1 -3 -1)
            br2 (m/mbr 0 1 1 -1)
            expected (m/mbr -1 1 -3 -1)]
        (is (= (m/union br1 br2) expected))))

    (testing "should not have to expand if it's already covered2"
      (let [br1 (m/mbr -1 1 -3 -1)
            br2 (m/mbr -5 1 -4 -1)
            expected (m/mbr -1 1 -3 -1)]
        (is (= (m/union br1 br2) expected))))

    (testing "should extend west to cover bounding rectangle that is closer to the west"
      (let [br1 (m/mbr 177 10 -174 -1)
            br2 (m/mbr 160 10 161 -1)
            expected (m/mbr 160 10 -174 -1)]
        (is (= (m/union br1 br2) expected))))

    (testing "should extend east to cover bounding rectangle that is closer to the east"
      (let [br1 (m/mbr 177 10 -174 -1)
            br2 (m/mbr -165 10 -161 -1)
            expected (m/mbr 177 10 -161 -1)]
        (is (= (m/union br1 br2) expected))))

    (testing "both covering whole world with no overlaps should result in whole world"
      (let [br1 (m/mbr -69.11 10 -128.78 -1)
            br2 (m/mbr -128.78 10 -69.11 -1)
            expected (m/mbr -180 10 180 -1)]
        (is (= (m/union br1 br2) expected))))

    (testing "both covering whole world with overlap on east should result in whole world"
      (let [br1 (m/mbr -69.11 10 -128.78 -1)
            br2 (m/mbr -128.78 10 -66.7 -1)
            expected (m/mbr -180 10 180 -1)]
        (is (= (m/union br1 br2) expected))))

    (testing "both covering whole world with overlap on west should result in whole world"
      (let [br1 (m/mbr -69.11 10 -128.78 -1)
            br2 (m/mbr -130 10 -69.11 -1)
            expected (m/mbr -180 10 180 -1)]
        (is (= (m/union br1 br2) expected))))

    (testing "both covering whole world with overlap on both sides should result in whole world"
      (let [br1 (m/mbr -69.11 10 -128.78 -1)
            br2 (m/mbr -130 10 -66 -1)
            expected (m/mbr -180 10 180 -1)]
        (is (= (m/union br1 br2) expected)))))

  (testing "both crossing the antimeridian"
    (testing "should extend east to cover bounding rectangle to the east"
      (let [br1 (m/mbr 155 10 -143 -1)
            br2 (m/mbr 178 10 -133 -1)]
        (is (= (m/union br1 br2) (m/mbr 155 10 -133 -1)))))

    (testing "should cover all longitudes if non-coverered areas don't overlap."
      (let [br1 (m/mbr 2 1 1 0)
            br2 (m/mbr -1 1 -2 0)]
        (is (= (m/union br1 br2) (m/mbr -180 1 180 0)))))

    (testing "should extend west to cover bounding rectangle to the east"
      (let [br1 (m/mbr 155 10 -143 -1)
            br2 (m/mbr 135 10 -178 -1)]
        (is (= (m/union br1 br2) (m/mbr 135 10 -143 -1))))))

  (testing "ending on antimeridian"

    (testing "both covering whole world with overlap should result in whole world"
      (let [br1 (m/mbr -180 10 10 -1)
            br2 (m/mbr 5 10 180 -1)
            expected (m/mbr -180 10 180 -1)]
        (is (= (m/union br1 br2) expected))))

    (testing "both covering whole world with no overlap should result in whole world"
      (let [br1 (m/mbr -180 10 10 -1)
            br2 (m/mbr 10 10 180 -1)
            expected (m/mbr -180 10 180 -1)]
        (is (= (m/union br1 br2) expected))))

    (doseq [lon [-180, 180]]
      (testing (format "starting on %d should expand east if closer" lon)
        (let [br1 (m/mbr lon 10 -155 -1)
              br2 (m/mbr -143 10 -142 -1)
              expected (m/mbr lon 10 -142 -1)]
          (is (= (m/union br1 br2) expected))))

      (testing (format "starting on %d should expand west if closer" lon)
        (let [br1 (m/mbr lon 10 -155 -1)
              br2 (m/mbr 172 10 174 -1)
              expected (m/mbr 172 10 -155 -1)]
          (is (= (m/union br1 br2) expected))))

      (testing (format "ending on %d should expand east if closer" lon)
        (let [br1 (m/mbr 173 10 lon -1)
              br2 (m/mbr -173 10 -164 -1)
              expected (m/mbr 173 10 -164 -1)]
          (is (= (m/union br1 br2) expected))))

      (testing (format "ending on %d should expand west if closer" lon)
        (let [br1 (m/mbr 173 10 lon -1)
              br2 (m/mbr 166 10 172 -1)
              expected (m/mbr 166 10 lon -1)]
          (is (= (m/union br1 br2) expected)))))))

