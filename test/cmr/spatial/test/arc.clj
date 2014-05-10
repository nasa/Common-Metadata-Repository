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
