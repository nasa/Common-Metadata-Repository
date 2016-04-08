(ns cmr.spatial.test.mbr
  (:require [clojure.test :refer :all]
            [cmr.common.test.test-check-ext :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]

            ;; my code
            [cmr.spatial.math :refer :all]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.point :as p]
            [cmr.spatial.test.generators :as sgen]
            [cmr.spatial.validation :as v]
            [cmr.spatial.messages :as msg]))

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

(defn larger?
  "Returns true if an mbr is larger than or equal to another mbr"
  [m1 m2]
  (let [{m1w :west m1n :north m1e :east m1s :south} m1
        {m2w :west m2n :north m2e :east m2s :south} m2]
    (and (>= (float->double m1n) (float->double m2n))
         (<= (float->double m1s) (float->double m2s))
         (>= (float->double m1e) (float->double m2e))
         (<= (float->double m1w) (float->double m2w)))))

(defn float-map->Mbr
  "Converts a map of floating point values into an Mbr"
  [{:keys [west north east south]}]
  (m/mbr (float->double west) (float->double north) (float->double east) (float->double south)))

(defspec round-to-float-map 1000
  (for-all [mbr sgen/mbrs]
    (let [rounded-smaller-map (m/round-to-float-map mbr false)
          rounded-larger-map (m/round-to-float-map mbr true)
          rounded-smaller (float-map->Mbr rounded-smaller-map)
          rounded-larger (float-map->Mbr rounded-larger-map)
          all-floats? #(every? float-type? (map % [:west :north :east :south]))]
      (and
        ;; Any rounded mbr is valid
        (empty? (v/validate rounded-smaller))
        (empty? (v/validate rounded-larger))

        ;; If the original crossed the antimeridian the rounded does as well
        (= (m/crosses-antimeridian? mbr) (m/crosses-antimeridian? rounded-smaller))
        (= (m/crosses-antimeridian? mbr) (m/crosses-antimeridian? rounded-larger))

        ;; The rounded to increase is larger than the mbr
        (larger? rounded-larger-map mbr)
        ;; The rounded to shrink is smaller than the mbr
        (larger? mbr rounded-smaller-map)

        ;; The values of the mbr are all floating points.
        (all-floats? rounded-smaller-map)
        (all-floats? rounded-larger-map)))))

(deftest round-to-float-map
  (testing "whole world"
    (testing "Cant grow larger than whole world"
      (is (= m/whole-world (float-map->Mbr (m/round-to-float-map m/whole-world true)))))
    (is (= (m/mbr -179.99998 90.0 180.0 -89.99999)
           (float-map->Mbr (m/round-to-float-map m/whole-world false))))))

(deftest br-validation-test
  (testing "valid br"
    (is (empty? (v/validate (m/mbr 0 0 0 0)))))
  (testing "invalid brs"
    (is (= [(msg/br-north-less-than-south 46 47)]
           (v/validate (m/mbr 0 46 0 47))))))

(defspec external-points-spec 100
  (for-all [mbr (gen/such-that (complement m/whole-world?) sgen/mbrs)]
    (let [external-points (m/external-points mbr)]
      (and (>= (count external-points) 3)
           (every? (complement (partial m/geodetic-covers-point? mbr))
                   external-points)))))

(defspec geodetic-covers-point-spec 100
  (for-all [mbr sgen/mbrs]
    (let [{w :west n :north e :east s :south} mbr
          corner-points (p/ords->points [w,n e,n e,s w,s])
          midlon (if (m/crosses-antimeridian? mbr)
                   (let [dist (+ (- 180 w) (- e -180))
                         half (/ dist 2.0)
                         mid (+ half w)]
                     (if (> mid 180) (- mid 360) mid))
                   (avg [w e]))
          midpoint (p/point midlon (avg [n s]))]
      (and
        (every? #(m/geodetic-covers-point? mbr %) corner-points)
        (m/geodetic-covers-point? mbr midpoint)))))

(deftest covers-point-test
  (let [examples [;; Normal
                  {:mbr [-10 25 10 -25]
                   :on [0 0
                        1 1]
                   :off [0 90
                         0 -90
                         180 0
                         0 26
                         -11 0
                         11 0
                         0 -26]}

                  ;; Touches north pole
                  {:mbr [-10 90 10 -25]
                   :on [0 0
                        1 1
                        0 90
                        180 90
                        90 90
                        -90 90
                        -180 90]
                   :off [0 -90
                         180 0
                         -11 0
                         11 0
                         0 -26]}

                  ;; Touches south pole
                  {:mbr [-10 25 10 -90]
                   :on [0 0
                        1 1
                        0 -90
                        180 -90
                        90 -90
                        -90 -90
                        -180 -90]
                   :off [0 90
                         180 0
                         0 26
                         -11 0
                         11 0]}]]
    (doseq [{mbr-parts :mbr
             on-ords :on
             off-ords :off} examples]
      (let [mbr (apply m/mbr mbr-parts)
            on-points (concat (p/ords->points on-ords)
                              (m/corner-points mbr))
            off-points (p/ords->points off-ords)]

        (doseq [p on-points]
          (is (m/geodetic-covers-point? mbr p) (str (pr-str mbr) (pr-str p))))
        (doseq [p off-points]
          (is (not (m/geodetic-covers-point? mbr p)) (str (pr-str mbr) (pr-str p))))))))

(deftest covers-br-test
  (testing "normal mbrs"
    (let [m1 (m/mbr -10 25 10 -25)]
      (are [w n e s]
           (m/covers-mbr? :geodetic m1 (m/mbr w n e s))

           ;; covers self
           -10 25 10 -25

           ;; Each part brought in by one
           -9 25 10 -25
           -10 24 10 -25
           -10 25 9 -25
           -10 25 10 -24

           ;; smaller mbr
           -9 24 9 -24

           ;; single point mbrs
           -10 25 -10 25 ; nw corner
           -10 -25 -10 -25  ; sw corner
           10 25 10 25 ; ne corner
           10 -25 10 -25)  ; se corner

      (are [w n e s]
           (not (m/covers-mbr? :geodetic m1 (m/mbr w n e s)))

           ;; Each part just outside mbr
           -11 25 10 -25
           -10 26 10 -25
           -10 25 11 -25
           -10 25 10 -26

           ;; Completely larger
           -11 26 11 -26)))
  (testing "crossing antimeridians"
    (let [m1 (m/mbr -10 25 10 -25) ; doesn't cross
          m2 (m/mbr 10 25 -10 -25) ; crosses antimeridian
          m3 (m/mbr 175 25 -175 -25) ; crosses antimeridian
          m4 (m/mbr 10 26 -10 25)] ; crosses antimeridian

      ;; Sanity check the test
      (is (not (m/crosses-antimeridian? m1)))
      (is (m/crosses-antimeridian? m2))
      (is (m/crosses-antimeridian? m3))
      (is (m/crosses-antimeridian? m4))

      (testing "covers self"
        (is (m/covers-mbr? :geodetic m1 m1))
        (is (m/covers-mbr? :geodetic m2 m2))
        (is (m/covers-mbr? :geodetic m3 m3))
        (is (m/covers-mbr? :geodetic m4 m4)))

      (testing "mbrs crossing antimeridian and non crossing"
        (is (not (m/covers-mbr? :geodetic m1 m2)))
        (is (not (m/covers-mbr? :geodetic m2 m1)))
        (is (not (m/covers-mbr? :geodetic m1 m3)))
        (is (not (m/covers-mbr? :geodetic m3 m1)))
        (is (m/covers-mbr? :geodetic m2 (m/mbr 176 9 177 9))))

      (testing "mbrs both crossing antimeridian"
        (is (m/covers-mbr? :geodetic m2 m3))
        (is (not (m/covers-mbr? :geodetic m3 m2)))
        (is (not (m/covers-mbr? :geodetic m2 m4)))
        (is (not (m/covers-mbr? :geodetic m4 m2)))))))

(defspec intersects-br-spec
  (for-all [mbr1 sgen/mbrs
            mbr2 sgen/mbrs]
    (and
      ;; intersect self
      (m/intersects-br? :geodetic mbr1 mbr1)
      (m/intersects-br? :geodetic mbr2 mbr2)

      ;; The union of the area intersects both
      (let [unioned (m/union mbr1 mbr2)]
        (and (m/intersects-br? :geodetic unioned mbr1)
             (m/intersects-br? :geodetic unioned mbr2)
             (m/intersects-br? :geodetic mbr1 unioned)
             (m/intersects-br? :geodetic mbr2 unioned)))

      ;; Inverse should be true
      (= (m/intersects-br? :geodetic mbr1 mbr2)
         (m/intersects-br? :geodetic mbr2 mbr1)))))

(deftest intersects-br-test
  (testing "one across antimeridian and one not"
    (is (m/intersects-br? :geodetic (m/mbr 180 85 180 0) (m/mbr 170 20 -170 10)))
    (is (m/intersects-br? :geodetic (m/mbr -170 85 -160 0) (m/mbr 170 20 -150 10))))

  (let [m1 (m/mbr -10 25 10 -25)]
    (are [w n e s]
         (let [m2 (m/mbr w n e s)]
           (and (m/intersects-br? :geodetic m1 m2)
                (m/intersects-br? :geodetic m2 m1)))
         ;; corners overlap
         9 -24 11 -26
         9 26 11 24

         ;; Completely within
         -9 24 9 -24

         ;; antimeridian
         11 -24 9 -26

         ;; No corners are in the other
         ;; They form a lower case t shape
         -9 26 9 -26)

    ;; t shape across antimeridian
    (let [m2 (m/mbr 160 5 -160 -5)
          m3 (m/mbr 170 25 -170 -25)]
      (is (m/intersects-br? :geodetic m2 m3))
      (is (m/intersects-br? :geodetic m3 m2)))

    (are [w n e s]
         (let [m2 (m/mbr w n e s)]
           (and (not (m/intersects-br? :geodetic m1 m2))
                (not (m/intersects-br? :geodetic m2 m1))))
         ;; to the right
         11 1 13 0
         11 26 13 -26

         ;; across antimeridian
         11 26 -11 -26

         ;; to the left
         -13 1 -11 0
         -13 26 -11 -26

         ;; above
         -9 27 9 26

         ;; below
         -9 -26 9 -27)))


(defspec intersections-br-spec 100
  (for-all [mbr1 sgen/mbrs
            mbr2 sgen/mbrs]
    (let [intersects? (m/intersects-br? :geodetic mbr1 mbr2)
          intersections (m/intersections mbr1 mbr2)]
      (or (and (not intersects?) (empty? intersections))

          (and (seq intersections)
           (every? #(and (m/covers-mbr? :geodetic mbr1 %)
                         (m/covers-mbr? :geodetic mbr2 %))
                   intersections))))))

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
        (every? #(m/covers-lon? :geodetic unioned %)
                (mapcat #(map (fn [k] (k %)) [:west :east]) [mbr1 mbr2]))

        (every? #(m/covers-lat? unioned %)
                (mapcat #(map (fn [k] (k %)) [:north :south]) [mbr1 mbr2]))))))

(defspec union-not-crossing-antimeridian-test 100
  (for-all [mbr1 sgen/mbrs-not-crossing-antimeridian
            mbr2 sgen/mbrs-not-crossing-antimeridian]
    (let [unioned (m/union-not-crossing-antimeridian mbr1 mbr2)]
      (and
        (not (m/crosses-antimeridian? unioned))
        ;; is commutative
        (= unioned (m/union-not-crossing-antimeridian mbr2 mbr1))

        ;; should cover all parts
        (every? #(m/covers-lon? :geodetic unioned %)
                (mapcat #(map (fn [k] (k %)) [:west :east]) [mbr1 mbr2]))

        (every? #(m/covers-lat? unioned %)
                (mapcat #(map (fn [k] (k %)) [:north :south]) [mbr1 mbr2]))))))

(defspec union-self-test 100
  (for-all [mbr sgen/mbrs]
    (and
      ;; union with self = self
      (= mbr (m/union mbr mbr))

      ;; union with reverse area = whole world unless west = east
      (if (not= (:west mbr) (:east mbr))
        (let [lon-flipped (m/mbr (:east mbr) (:north mbr) (:west mbr) (:south mbr))]
          (= (m/mbr -180 (:north mbr) 180 (:south mbr)) (m/union mbr lon-flipped)))
        true))))


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

