(ns cmr.spatial.test.arc
  (:require [clojure.test :refer :all]
            ; [clojure.test.check.clojure-test :refer [defspec]]
            ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
            [cmr.common.test.test-check-ext :refer [defspec]]

            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]

            ;; my code
            [cmr.spatial.math :refer :all]
            [cmr.spatial.arc :as a]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.test.generators :as sgen]))

(defspec arc-equivalency-spec 100
  (for-all [arc sgen/arcs]
    (let [{:keys [west-point east-point]} arc]
      (and (= arc arc)
           (= (a/arc west-point east-point)
              (a/arc west-point east-point))
           (not= arc (a/arc (p/antipodal west-point) east-point))
           (not= arc (a/arc west-point (p/antipodal east-point)))))))

(defspec arc-great-circles-spec 100
  (for-all [arc sgen/arcs]
    (let [{:keys [west-point east-point great-circle]} arc
          antipodal-arc (a/arc (p/antipodal west-point) (p/antipodal east-point))]
      ;; The antipodal arc should lie on the same great circle
      (approx= great-circle (:great-circle antipodal-arc)))))

(defn- assert-gc-extreme-points [arc expected-northern expected-southern]
  (let [{{:keys [northernmost-point
                 southernmost-point]} :great-circle} arc]
    (is (approx= expected-northern northernmost-point))
    (is (approx= expected-southern southernmost-point))))

(defspec arc-midpoint-spec 100
  (for-all [arc sgen/arcs]
    (let [midpoint (a/midpoint arc)]
      (and (or (mbr/covers-point? (:mbr1 arc) midpoint)
               (and (:mbr2 arc)
                    (mbr/covers-point? (:mbr2 arc) midpoint)))))))

(deftest arc-midpoint-test
  (are [ords lon-lat-midpoint]
       (= (apply p/point lon-lat-midpoint)
          (a/midpoint (apply a/ords->arc ords)))

       ;; Normal arc
       [0 0 10 10] [5 5.057514896828208]

       ;; vertical
       [1 2, 1 10] [1 6]

       ;; vertical on antimeridian
       [180 2, 180 10] [180 6]
       [180 2, -180 10] [180 6]
       [-180 2, 180 10] [-180 6]
       [-180 2, -180 10] [-180 6]

       ;; across north pole
       [0 85, 180 85] [0 90]
       [-10 85, 170 85] [0 90]

       ;; across south pole
       [0 -85, 180 -85] [0 -90]
       [-10 -85, 170 -85] [0 -90]))


(deftest arc-vertical-test
  (testing "vertical cases"
    (are [ords]
         (and
           (a/vertical? (apply a/ords->arc ords))
           (a/vertical? (apply a/ords->arc (flatten (reverse (partition 2 ords))))))
         [1 2, 1 10]
         ; One point on a pole
         [5,5, 0 90]
         [5,5, 0 -90]

         ;; on antimeridian
         [180 2, 180 10]
         [180 2, -180 10]
         [-180 2, 180 10]
         [-180 2, -180 10]
         [44.99999999999999 -40.28192423875854, 45.0 -37.143419950509745]
         ))
  (testing "not vertical cases"
    (are [ords]
         (not (a/vertical? (apply a/ords->arc ords)))
         [1 0, 2 0]
         [1 0, 2 1]
         [1 0, 2 1]
         ;; across pole
         [-10 85, 170 85]
         [0 85, 180 85]
         [0 85, -180 85])))


(deftest arc-great-circles-examples
  (testing "Normal set of points"
    (assert-gc-extreme-points
      (a/arc (p/point 1 2) (p/point 10 11))
      (p/point 89.04318347568332 45.64248773552605)
      (p/point -90.95681652431668 -45.64248773552605)))
  (testing "across antimeridian"
    (assert-gc-extreme-points
      (a/arc (p/point -165 65) (p/point 175 35))
      (p/point -104.15192788812082 77.20236093320518)
      (p/point 75.84807211187918 -77.20236093320518)))
  (testing "along equator"
    (assert-gc-extreme-points
      (a/arc (p/point -10 0) (p/point 10 0))
      (p/point 0 0)
      (p/point 180 0)))
  (testing "points with matching latitude"
    (assert-gc-extreme-points
      (a/arc (p/point -10, 45) (p/point 10 45))
      (p/point 0 45.4385485867423)
      (p/point 180 -45.4385485867423))))

(deftest crosses-poles
  (letfn [(over-north [& ords]
            (a/crosses-north-pole? (apply a/ords->arc ords)))
          (over-south [& ords]
            (a/crosses-south-pole? (apply a/ords->arc ords)))]
  (testing "crosses north pole"
    (is (over-north -90,85, 90,85))
    (is (over-north -180,85, 0,85))
    (is (not (over-north -180,-85, 0,-85)))
    (is (not (over-north 1,2 3,4))))
  (testing "crosses south pole"
    (is (over-south -90,-85, 90,-85))
    (is (over-south -180,-85, 0,-85))
    (is (not (over-south -180,85, 0,85)))
    (is (not (over-south 1,2 3,4))))))

(defspec arc-bounding-rectangles-spec 100
  (for-all [arc sgen/arcs]
    (let [brs (a/mbrs arc)]
      (cond
        (a/crosses-north-pole? arc)
        (and (= (count brs) 2)
             (every? #(-> :north (= 90)) brs))
        (a/crosses-south-pole? arc)
        (and (= (count brs) 2)
             (every? #(-> :south (= -90)) brs))
        :else
        (and
          (= (count brs) 1)
          (let [[br] brs
                {:keys [west-point east-point]} arc]
            (and
              (mbr/covers-point? br west-point)
              (mbr/covers-point? br east-point))))))))
