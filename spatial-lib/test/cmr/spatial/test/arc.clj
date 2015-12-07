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
            [cmr.spatial.vector :as v]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.test.generators :as sgen]))

(defspec arc-equivalency-spec 1000
  (for-all [arc sgen/arcs]
    (let [{:keys [west-point east-point]} arc]
      (and (= arc arc)
           (= (a/arc west-point east-point)
              (a/arc west-point east-point))
           (not= arc (a/arc (p/antipodal west-point) east-point))
           (not= arc (a/arc west-point (p/antipodal east-point)))))))

(defspec arc-great-circles-spec 1000
  (for-all [arc sgen/arcs]
    (let [{:keys [west-point east-point great-circle]} arc
          antipodal-arc (a/arc (p/antipodal west-point) (p/antipodal east-point))
          gc1 (:great-circle arc)
          gc2 (:great-circle antipodal-arc)]
      ;; The antipodal arc should lie on the same great circle
      (and (approx= (:northernmost-point gc1)
              (:northernmost-point gc2))
           (approx= (:southernmost-point gc1)
              (:southernmost-point gc2))
           (v/parallel? (:plane-vector gc1)
                        (:plane-vector gc2))))))

(defn- assert-gc-extreme-points [arc expected-northern expected-southern]
  (let [{{:keys [northernmost-point
                 southernmost-point]} :great-circle} arc]
    (is (approx= expected-northern northernmost-point))
    (is (approx= expected-southern southernmost-point))))

(defspec arc-midpoint-spec 1000
  (for-all [arc sgen/arcs]
    (let [midpoint (a/midpoint arc)]
      (and (or (mbr/geodetic-covers-point? (:mbr1 arc) midpoint)
               (and (:mbr2 arc)
                    (mbr/geodetic-covers-point? (:mbr2 arc) midpoint)))
           (a/point-on-arc? arc midpoint)))))

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

         ;; across pole
         [-10 85, 170 85]
         [0 85, 180 85]
         [0 85, -180 85]))
  (testing "not vertical cases"
    (are [ords]
         (not (a/vertical? (apply a/ords->arc ords)))
         [1 0, 2 0]
         [1 0, 2 1]
         [1 0, 2 1])))


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

(defspec point-on-arc-spec 1000
  (for-all [arc sgen/arcs]
    (let [midpoint (a/midpoint arc)]
      ;;The midpoint of the arc should be on the arc.
      (a/point-on-arc? arc midpoint))))

(deftest point-on-arc-test
  (let [examples [;; normal arc
                  {:arc [0,0, 10,10]
                   :on [7.101116 7.154793
                        3.8992084 3.9500345]
                   :off [8 4
                         0 90
                         0 -90
                         180 0]}

                  ;; across north pole
                  {:arc [0 85 180 85]
                   :on [0 90
                        180 88
                        0 88]
                   :off [0 84.9
                         180 84.9
                         0 -90]}

                  ;; across south pole
                  {:arc [0 -85 180 -85]
                   :on [0 -90
                        180 -88
                        0 -88]
                   :off [0 -84.9
                         180 -84.9
                         0 90]}]]
    (doseq [{arc-ords :arc
             on-ords :on
             off-ords :off} examples]
      (let [arc-points (p/ords->points arc-ords)
            arc (apply a/arc arc-points)
            on-points (concat (p/ords->points on-ords)
                              arc-points)
            off-points (concat (p/ords->points off-ords)
                               (map p/antipodal arc-points))
            on-arc? (partial a/point-on-arc? arc)]
        (doseq [p on-points]
          (is (a/point-on-arc? arc p)
              (pr-str `(a/point-on-arc? (a/ords->arc ~@(a/arc->ords arc))
                                        (p/point ~(:lon p) ~(:lat p))))))
        (doseq [p off-points]
          (is (not (a/point-on-arc? arc p))
              (pr-str `(a/point-on-arc? (a/ords->arc ~@(a/arc->ords arc))
                                        (p/point ~(:lon p) ~(:lat p))))))))))

(defn print-points-at-lat-failure
  [type arc lat]
  (println "arc:" (pr-str `(~'a/ords->arc ~@(a/arc->ords arc))))
  (println "lat:" lat))

(defspec points-at-lat-spec {:times 100 :printer-fn print-points-at-lat-failure}
  (for-all [a sgen/arcs
            lat sgen/lats]
    (let [points (a/points-at-lat a lat)
          mbrs (a/mbrs a)]
      (and (every? #(some (fn [mbr] (mbr/geodetic-covers-point? mbr %)) mbrs)
              points)
           (every? (partial a/point-on-arc? a) points)))))


(defn print-lat-segment-intersections-failure
  [type arc lat lon-w lon-e]
  (println "arc:" (pr-str `(~'a/ords->arc ~@(a/arc->ords arc))))
  (println "lat:" lat "lon-w" lon-w "lon-e" lon-e))

(defspec lat-segment-intersections-spec {:times 100 :printer-fn print-lat-segment-intersections-failure}
  (for-all [arc sgen/arcs
            lat sgen/lats
            lon-w sgen/lons
            lon-e sgen/lons]
    (let [intersections (a/lat-segment-intersections arc lat lon-w lon-e)
          brs (a/mbrs arc)
          lat-br (mbr/mbr lon-w lat lon-e lat)]
      ;; If there are intersections they should all be on the arc
      (every? (partial a/point-on-arc? arc) intersections))))


(deftest lat-segment-intersections-test
  ;; Examples include on and off which are sets of triples. Each triple is lat lon-west and lon-east
  (let [fail-printer (fn [arc [lat lon-west lon-east]]
                       (pr-str (list 'a/lat-segment-intersections
                                     (cons 'a/ords->arc (a/arc->ords arc))
                                     lat lon-west lon-east)))
        examples [;; Normal arc
                  {:arc [0 0 10 10]
                   :on [[5.191 4 7]
                        ;; across antimeridian
                        [5.191 7 6]
                        ;; across whole earth
                        [0 -180 180]
                        [10 -180 180]
                        [5 -180 180]
                        ;; On endpoints
                        [0 -10 0]
                        [0 0 10]
                        [10 0 10]
                        [10 10 20]]
                   :off [[5.191 5.276 7]
                         ;; across antimeridian
                         [5.191 7 4]
                         ;; above
                         [10.1 -180 180]
                         ; below
                         [-0.1 -180 180]]}

                   ;; at northermost point of gc
                  {:arc [-40.29,84.82, -140.38,85.08]
                   :on [[86.75493561135306 -91.57687352243259 -40]
                        [86 -180 0]
                        [86 -90 0]
                        [86 -180 180]]
                   :off [[86 0 -180]
                         [86 0 180]
                         [86 90 180]
                         [-86 -180 180]]}

                   ;; at southermost point of gc
                  {:arc [-40.29,-84.82, -140.38,-85.08]
                   :on [[-86.75493561135306 -91.57687352243259 -40]
                        [-86 -180 0]
                        [-86 -90 0]
                        [-86 -180 180]]
                   :off [[-86 0 -180]
                         [-86 0 180]
                         [-86 90 180]
                         [86 -180 180]]}

                   ;; vertical
                  {:arc [10 0 10 20]
                   :on [[0 9 11]
                        [5 9 11]
                        [10 9 11]
                        [20 10 11]
                         ;; across antimeridian
                        [5 11 10]]
                   :off [[5 11 12]
                         [5 8 9]
                         ;; across antimeridian
                         [5 11 9]]}

                   ;; point on north pole
                  {:arc [0 90 165 85]
                   :on [[90 0 1]
                        [87 164 166]
                        [85 -180 180]]
                   :off [[87 166 167]
                         [87 163 164]
                         [84 -180 180]]}

                   ;; point on south pole
                  {:arc [0 -90 165 -85]
                   :on [[-90 0 1]
                        [-87 164 166]
                        [-85 -180 180]]
                   :off [[-87 166 167]
                         [-87 163 164]
                         [-84 -180 180]]}

                   ;; across north pole
                  {:arc [0 85 180 85]
                   :on [[87 -180 180]
                        [85 -180 180]
                        [87 -1 1]
                        [87 175 -175]]
                   :off [[84 -180 180]
                         [87 1 2]
                         [87 -2 -1]]}]]
    (doseq [{arc-ords :arc
             on-ord-tuples :on
             off-ord-tuples :off} examples]
      (let [arc (apply a/ords->arc arc-ords)]
        (doseq [on-ords on-ord-tuples]
          (let [intersections (apply a/lat-segment-intersections arc on-ords)]
            (is (not (empty? intersections)) (fail-printer arc on-ords))
            (is (every? (partial a/point-on-arc? arc) intersections) (fail-printer arc on-ords))))
        (doseq [off-ords off-ord-tuples]
          (is (empty? (apply a/lat-segment-intersections arc off-ords))
              (fail-printer arc off-ords)))))))

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

(defspec arc-bounding-rectangles-spec 1000
  (for-all [arc sgen/arcs]
    (let [brs (a/mbrs arc)]
      (cond
        (a/crosses-north-pole? arc)
        (and (= (count brs) 2)
             (every? #(-> % :north (approx= 90.0)) brs))
        (a/crosses-south-pole? arc)
        (and (= (count brs) 2)
             (every? #(-> % :south (approx= -90.0)) brs))
        :else
        (and
          (= (count brs) 1)
          (let [[br] brs
                {:keys [west-point east-point]} arc]
            (and
              (mbr/geodetic-covers-point? br west-point)
              (mbr/geodetic-covers-point? br east-point))))))))
