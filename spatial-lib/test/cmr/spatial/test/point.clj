(ns cmr.spatial.test.point
  (:require [clojure.test :refer :all]

            ; [clojure.test.check.clojure-test :refer [defspec]]
            ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
            [cmr.common.test.test-check-ext :refer [defspec]]

            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]

            ;;my code
            [cmr.spatial.test.generators :as sgen]
            [cmr.spatial.point :as p]
            [cmr.spatial.validation :as v]
            [cmr.spatial.messages :as msg]
            [cmr.spatial.math :refer :all])
  (:import cmr.spatial.point.Point))

(defn- point-matches? [^Point p1 ^Point p2]
  (and
    (= 1 (count (set [p1 p2])))
    (= p1 p2)
    (= p2 p1)
    (.equals p1 p2)
    (.equals p2 p1)
    (= (hash p1) (hash p2))))

(defn- hash-code-equals-consistent?
  "Ensures that the hash code and equals values are equivalent."
  [p1 p2]
  (let [h1 (hash p1)
        h2 (hash p2)]
    ;; If the points are equal the hash code should be equal
    (or (and (= p1 p2) (= h1 h2))
        ;;Otherwise the points should not be equal
        (not= p1 p2))))

(defspec point-hashCode-equals-consistency 1000
  (for-all [^Point p1 sgen/points
            ^Point p2 sgen/points]
    (and
      (hash-code-equals-consistent? p1 p2)
      ;; Order of comparison is equivalent.
      (= (= p1 p2) (.equals p1 p2) (= p2 p1) (.equals p2 p1))
      (point-matches? p1 (p/point (:lon p1) (:lat p1)))
      (point-matches? p2 (p/point (:lon p2) (:lat p2))))))

(deftest point-equality-hash
  (testing "geodetic equality"
    (testing "at poles"
      (let [north-poles (map #(p/point % 90) (range -180 181 10))
            south-poles (map #(p/point % -90) (range -180 181 10))]
        (is (= 1 (count (set (map hash north-poles)))))
        (is (= 1 (count (set (map hash south-poles)))))
        (is (= 1 (count (set north-poles))))
        (is (= 1 (count (set south-poles))))
        (is (apply = north-poles))
        (is (apply = south-poles))

        ;; North poles don't equal south poles
        (is (not (some #(= (p/point (:lon %) -90) %) north-poles)))
        ;; North poles don't equal points near the north pole
        (is (not (some #(= (p/point (:lon %) 89.999) %) north-poles)))

        ;; South poles don't equal points near the south pole
        (is (not (some #(= (p/point (:lon %) -89.99) %) south-poles)))))
    (testing "very close on pole"
      (let [p1 (p/point 0.0 89.99999999999996)
            p2 (p/point 0.0 90.0)]
        (is (= p1 p2))
        (is (= (hash p1) (hash p2)))))
    (testing "antimeridian"
      (is (= (p/point -180 0) (p/point 180 0)))
      (is (= (p/point -180 10) (p/point 180 10)))
      (is (= (p/point 180 10) (p/point -180 10)))
      (is (= (hash (p/point -180 0)) (hash (p/point 180 0))))
      (is (= (hash (p/point -180 10)) (hash (p/point 180 10))))

      (is (not= (p/point -180 -10) (p/point -180 10))))
    (testing "nil and other classes"
      (is (not= (p/point 0 1) nil))
      (is (not (.equals ^Point (p/point 0 1) nil)))
      (is (not= (p/point 0 1) {:lon 0 :lat 1}))
      (is (not= (p/point 0 1) "foo"))
      (is (not (.equals ^Point (p/point 0 1) "foo")))))

  (testing "cartesian equality"
    (testing "poles"
      (is (not= (p/point 0 90 false) (p/point 1 90 false)))
      (is (not= (p/point 180 90 false) (p/point -180 90 false)))
      (is (= (p/point 8 90 false) (p/point 8 90 false)))
      (is (not= (p/point 0 -90 false) (p/point 1 -90 false)))
      (is (not= (p/point 180 -90 false) (p/point -180 -90 false))))
    (testing "antimeridian"
      (is (not= (p/point 180 10 false) (p/point -180 10 false)))
      (is (= (p/point 180 10 false) (p/point 180 10 false))))
    (testing "normal"
      (is (= (p/point 54 45 false) (p/point 54 45 false)))
      (is (not= (p/point 54 45 false) (p/point 54 46 false)))
      (is (not= (p/point 55 45 false) (p/point 54 45 false))))))

(deftest point-validation
  (testing "valid point"
    (is (nil? (seq (v/validate (p/point 0 0))))))
  (testing "invalid points"
    (are [lon lat msg]
         (= [msg]
            (v/validate (p/point lon lat)))
         -181 0 (msg/point-lon-invalid -181)
         181 0 (msg/point-lon-invalid 181)
         0 90.1 (msg/point-lat-invalid 90.1)
         0 -90.1 (msg/point-lat-invalid -90.1))))

;; Tests that when associating a new subvalue to a point it stays consistent.
(defspec point-assoc 100
  (for-all [p sgen/points
            lon sgen/lons
            lat sgen/lats]
    (and
      (approx= (radians lon) (:lon-rad (assoc p :lon lon)))
      (approx= (radians lat) (:lat-rad (assoc p :lat lat)))
      (approx= lon (:lon (assoc p :lon-rad (radians lon))))
      (approx= lat (:lat (assoc p :lat-rad (radians lat)))))))

(deftest pole-detection
  (testing "is-north-pole?"
    (is (p/is-north-pole? (p/point 0 90)))
    (is (not (p/is-north-pole? (p/point 0 -90))))
    (is (not (p/is-north-pole? (p/point 0 89.999999)))))
  (testing "is-south-pole?"
    (is (p/is-south-pole? (p/point 0 -90)))
    (is (not (p/is-south-pole? (p/point 0 90))))
    (is (not (p/is-south-pole? (p/point 0 -89.999999))))))

(deftest antipodal-points
  (testing "north and south poles"
    (is (p/antipodal? (p/point 0 90) (p/point 0 -90)))
    (is (p/antipodal? (p/point 0 -90) (p/point 0 90)))
    (is (p/antipodal? (p/point 10 90) (p/point 0 -90)))

    (is (not (p/antipodal? (p/point 0 90) (p/point 0 90))))
    (is (not (p/antipodal? (p/point 0 -90) (p/point 0 -90)))))

  (testing "other places"
    (is (p/antipodal? (p/point 45 25) (p/point -135 -25)))
    (is (not (p/antipodal? (p/point 46 25) (p/point -135 -26))))
    (is (not (p/antipodal? (p/point 45 26) (p/point -136 -25))))))


;; Commenting out this test as we don't want assertions enabled everywhere.
#_(deftest point-assertions
    (testing "longitude and latitude are validated"
      (are [lon lat] (thrown? AssertionError (p/point lon lat))
           -181 0
           181 0
           0 91
           0 -91))
    (testing "lon-rad and lat-rad are validated"
      (are [lon lat lon-rad lat-rad] (thrown? AssertionError (p/point lon lat lon-rad lat-rad))
           0 0 (radians 0.3) (radians 0)
           0 0 (radians 0) (radians 1))))

(deftest point-approx=
  (testing "on antimeridian"
    (is (approx= (p/point 180 0.0)
                 (p/point -180 0)))
    (is (not (approx= (p/point -180 10)
                      (p/point -180 0)))))
  (testing "on north pole"
    (is (approx= (p/point 1 90)
                 (p/point 0 90)))
    (is (not (approx= (p/point 0 89.99)
                      (p/point 0 90))))
    (is (not (approx= (p/point 0 -90)
                      (p/point 0 90)))))
  (testing "on south pole"
    (is (approx= (p/point 1 -90)
                 (p/point 0 -90)))
    (is (not (approx= (p/point 0 -89.99)
                      (p/point 0 -90))))))


(defn- crosses-at-most-180? [l1 l2]
  (if (>= l2 l1)
    (<= (- l2 l1) 180)
    (<= (+ (- 180 l1)
           (- l2 -180))
        180)))

(deftest compare-points-test
  (testing "when longitudes match"
    (let [p1 (p/point 95 23)
          p2 (p/point 95 22)]
      (is (> (p/compare-points p1 p2) 0))
      (is (< (p/compare-points p2 p1) 0))))
  (testing "when 180 degrees apart"
    (let [p1 (p/point 180 25)
          p2 (p/point 0 25)]
      (is (> (p/compare-points p1 p2) 0))
      (is (< (p/compare-points p2 p1) 0)))
    (let [p1 (p/point -180 25)
          p2 (p/point 0 25)]
      (is (< (p/compare-points p1 p2) 0))
      (is (> (p/compare-points p2 p1) 0)))))

(defn order-points
  "Orders the points using the compare points function"
  [p1 p2]
  (if (<= (p/compare-points p1 p2) 0)
    [p1 p2]
    [p2 p1]))

(defspec order-points-test 100
  (for-all [p1 sgen/points
            p2 sgen/points]
    (let [[op1 op2] (order-points p1 p2)]
      (and
        ;; should have original points
        (= #{op1 op2} #{p1 p2})
        ;; should stay in order
        (= [op1 op2] (order-points op1 op2))
        ;; should be put back in the right order
        (= [op1 op2] (order-points op2 op1))
        (crosses-at-most-180? (:lon op1) (:lon op2))))))

(defspec compare-longitudes-test 100
  (for-all [lon1 sgen/lons
            lon2 sgen/lons]
    (let [compare-l1-l2 (p/compare-longitudes lon1 lon2)
          compare-l2-l1 (p/compare-longitudes lon2 lon1)]
      (or (not= compare-l1-l2 compare-l2-l1)
          (= lon1 lon2)))))

(defspec angular-distance-spec 100
  (for-all [[p1 p2] (sgen/non-antipodal-points 2)]
    (let [a1 (degrees (p/angular-distance p1 p2))
          a2 (degrees (p/angular-distance p2 p1))
          a3 (degrees (p/angular-distance p1 (p/antipodal p2)))
          a4 (degrees (p/angular-distance p2 (p/antipodal p1)))]
      (and
        ;; Reversed points gives the same angular distance
        (approx= a1 a2)
        ;; The antipodal point of either is on the same great circle. There's 360 degrees in a circle
        ;; The antipodal point is on the opposite side a distance of 180 degrees. So the angular distance
        ;; to the antipodal point is 180 - the original angular distance
        (approx= a3 (- 180 a1))
        (approx= a3 a4)

        ;; The same point is always 0
        (approx= 0 (p/angular-distance p1 p1))
        (approx= 0 (p/angular-distance p2 p2))))))

(defspec course-spec 100
  (for-all [[p1 p2] (sgen/non-antipodal-points 2)]
    (let [course (p/course p1 p2)]
      (and (>= course 0)
           (< course 360)))))

(deftest course
  (testing "should not fail when close to a pole"
    (are [lat] (> (p/course (p/point 0 0) (p/point 45 lat))
                  359.99)
         89.99
         89.999
         89.9999
         89.99999
         89.999999)
    (are [lat] (= (p/course (p/point 0 0) (p/point 45 lat))
                  0.0)
         89.9999999
         89.99999999
         89.999999999
         89.9999999999))
  (testing "should return 0 when pointing directly at north pole"
    (let [p1 (p/point 0 0)
          p2 (p/point 0 1)]
      (approx= (p/course p1 p2) 0))
    ;; Another example that previously caused a numerical error
    (let [p1 (p/point 4 4)
          p2 (p/point 4 5)]
      (approx= (p/course p1 p2) 0)))
  (testing "should return 180 when pointing directly away from north pole"
    (let [p1 (p/point 0 1)
          p2 (p/point 0 0)]
      (approx= (p/course p1 p2) 180))
    ;; Another example that previously caused a numerical error
    (let [p1 (p/point 4 5)
          p2 (p/point 4 4)]
      (approx= (p/course p1 p2) 180)))
  (testing "should return 270 when pointing due east along equator"
    (let [p1 (p/point 0 0)
          p2 (p/point 10 0)]
      (approx= (p/course p1 p2) 270)))
  (testing "should return 90 when pointing due west along equator"
    (let [p1 (p/point 10 0)
          p2 (p/point 0 0)]
      (approx= (p/course p1 p2) 90)))
  (testing "should return 0 when point 2 is on north pole"
    (let [p1 (p/point 10 12)
          p2 (p/point 10 90)]
      (approx= (p/course p1 p2) 0)))
  (testing "should return 180 when point 2 is on south pole"
    (let [p1 (p/point 10 12)
          p2 (p/point 10 -90)]
      (approx= (p/course p1 p2) 180)))
  (testing "should return 180 when point 1 is on north pole"
    (let [p1 (p/point 55 90)
          p2 (p/point 5 3)]
      (approx= (p/course p1 p2) 180)))
  (testing "should return 0 when point 1 is on south pole"
    (let [p1 (p/point 55 -90)
          p2 (p/point 5 3)]
      (approx= (p/course p1 p2) 0)))
  (testing "should take into account earth curvature"
    (let [p1 (p/point 135 85)
          p2 (p/point -135 85)]
      (approx= (p/course p1 p2) 315.0)))
  (testing "should be 0 when crossing north pole"
    (let [p1 (p/point -90 85)
          p2 (p/point 90 85)]
      (approx= (p/course p1 p2) 0.0)))
  (testing "should be 180 when crossing south pole"
    (let [p1 (p/point -90 -85)
          p2 (p/point 90 -85)]
      (approx= (p/course p1 p2) 180.0)))
  (testing "should not result in numerical error when going across the poles"
    (doseq [lat (range 1 89)
            hemisphere [:north :south]
            :let [lat (if (= hemisphere :south) (* -1 lat) lat)
                  course (p/course (p/point 0 lat) (p/point 180 lat))]]
      (if (= hemisphere :north)
        (is (approx= course 0))
        (is (approx= course 180))))))