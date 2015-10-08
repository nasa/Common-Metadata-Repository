(ns cmr.spatial.test.line-segment
  (:require [clojure.test :refer :all]
            [cmr.common.test.test-check-ext :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]

            ;; my code
            [cmr.spatial.math :refer :all]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.point :as p]
            [cmr.spatial.line-segment :as s]
            [cmr.spatial.derived :as d]

            [cmr.spatial.test.generators :as sgen]
            [cmr.spatial.validation :as v]
            [cmr.spatial.messages :as msg]
            [clojure.string :as str]))

(primitive-math/use-primitive-operators)

(defn valid-double?
  [^double v]
  (and (not (Double/isInfinite v))
       (not (Double/isNaN v))))

(defspec line-segment-calculate-derived-spec {:times 1000 :printer-fn sgen/print-failed-line-segments}
  (for-all [ls sgen/line-segments]
    (let [{:keys [b m mbr point1 point2]} ls]
      (and
        (or (s/vertical? ls)
            (and
              (valid-double? b)
              (valid-double? m)))

        (not (m/crosses-antimeridian? mbr))
        (m/cartesian-covers-point? mbr point1)
        (m/cartesian-covers-point? mbr point2)))))

(defspec segment+lon->lat-spec {:times 1000 :printer-fn sgen/print-failed-line-segments}
  (for-all [ls (gen/such-that (complement s/vertical?) sgen/line-segments)]
    (let [{:keys [point1 point2]} ls]
      (and
        (approx= (:lat point1) (s/segment+lon->lat ls (:lon point1)))
        (approx= (:lat point2) (s/segment+lon->lat ls (:lon point2)))))))

(defspec segment+lat->lon-spec {:times 1000 :printer-fn sgen/print-failed-line-segments}
  (for-all [ls (gen/such-that (complement s/horizontal?) sgen/line-segments)]
    (let [{:keys [point1 point2]} ls]
      (and
        (approx= (:lon point1) (s/segment+lat->lon ls (:lat point1)))
        (approx= (:lon point2) (s/segment+lat->lon ls (:lat point2)))))))

(defspec densify-line-segment {:times 1000 :printer-fn sgen/print-failed-line-segments}
  (for-all [ls sgen/line-segments]
    (let [;; picked a larger amount to keep test fast since points may be very far apart
          densification-dist 5.0
          original-dist (s/distance ls)
          {:keys [point1 point2]} ls
          points (s/densify-line-segment ls densification-dist)]
      (and
        ;; every new point should be on the line segment
        (every? (partial s/point-on-segment? ls) points)

        ;; first and last points should match the original segment
        (= point1 (first points))
        (= point2 (last points))

        (or (and (s/vertical? ls)
                 (= (count points) 2)) ; vertical lines won't be densified

            ;; Check densification distances
            (let [distances (map (partial apply s/distance) (partition 2 1 points))
                  distance-sum (apply clojure.core/+ distances)]
              (and
                ;; The sum of the distances should equal the original distance
                (approx= distance-sum original-dist)

                ;; distance between each should be approximately the densification distance
                ;; except for at the last point
                (every? (partial approx= densification-dist) (drop-last distances))

                ;; The last distance should be less than or equal to the densification distance
                (<= ^double (last distances) densification-dist))))))))

(defspec line-segment-intersection-spec {:times 1000 :printer-fn sgen/print-failed-line-segments}
  (for-all [ls1 sgen/line-segments
            ls2 sgen/line-segments]
    (let [mbr1 (:mbr ls1)
          mbr2 (:mbr ls2)
          point (s/intersection ls1 ls2)]
      (or (nil? point)
          (and (m/cartesian-covers-point? mbr1 point)
               (m/cartesian-covers-point? mbr2 point)
               (s/point-on-segment? ls1 point)
               (s/point-on-segment? ls2 point))))))

(deftest line-segment-example-intersections
  (are [ords1 ords2 intersection-point]
       (or (and (nil? intersection-point)
                (nil? (s/intersection (apply s/ords->line-segment ords1)
                                      (apply s/ords->line-segment ords2))))
           (approx= intersection-point (s/intersection (apply s/ords->line-segment ords1)
                                                       (apply s/ords->line-segment ords2))))

       ;; T intersection
       [3 5, 3 -5] [2 5, 4 5] (p/point 3 5)

       ;; updside down T intersection
       [3 5, 3 -5] [2 -5, 4 -5] (p/point 3 -5)

       ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
       ;; Parallel
       ;; Different slope intercepts
       [2 -2, 4 0] [2 -3, 4 -1] nil
       ;; Matching slope interscepts but different ranges
       [1 1, 2 2] [3 3, 4 4] nil

       ;; Intersect at end point
       [1 1, 2 2] [2 2, 4 4] (p/point 2 2)

       ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
       ;; both vertical
       ;; with different longitudes
       [-10 50 -10 80] [-11 50 -11 80] nil
       ;; with different vertical ranges
       [-10 50 -10 80] [-10 82 -10 85] nil
       ;; intersecting
       [-10 50 -10 80] [-10 60 -10 85] (p/point -10 60)

       ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
       ;; both horizontal
       ;; with different latitudes
       [10 5 40 5] [10 6 40 6] nil
       ;; different ranges
       [10 5 40 5] [50 5 60 5] nil
       ;; intersecting
       [10 5 40 5] [20 5 60 5] (p/point 20 5)

       ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
       ;; normal
       [1 7 10 8] [1 8 10 7] (p/point 5.5 7.5)))

(defn print-subselect-failure
  [type ls mbr]
  (sgen/print-failed-line-segments type ls)
  (sgen/print-failed-mbrs type mbr))

(defn approx-equal-within-magnitude
  "Determines if two numbers are approximately equal within a certain percent of their magnitude.
  This is useful for comparing lines since the b and the m values can be enormous for close to vertical lines"
  [^double n1 ^double n2 ^double percent-diff]
  (let [avg-magnitude (avg [(abs n1) (abs n2)])
        delta (* avg-magnitude (/ percent-diff 100.0))]
    (or (approx= n1 n2 delta)
        ;; Handles cases where the values are very small.
        (approx= n1 n2 0.0000001))))

(defn valid-subselected-line-segment?
  "Returns true if sub-ls is a valid line segment that was subselected from ls with mbr."
  [ls mbr sub-ls]
  (let [{:keys [point1 point2]} sub-ls
        ls-mbr (:mbr ls)]
    (and (or (and (s/vertical? ls) (s/vertical? sub-ls))
             (and (approx-equal-within-magnitude (:m ls) (:m sub-ls) 0.01)
                  (approx-equal-within-magnitude (:b ls) (:b sub-ls) 0.01)))
         (m/cartesian-covers-point? mbr point1 0.00001)
         (m/cartesian-covers-point? mbr point2 0.00001)
         (m/cartesian-covers-point? ls-mbr point1 0.00001)
         (m/cartesian-covers-point? ls-mbr point2 0.00001))))

(defn valid-subselected-point?
  "Returns true if point is a valid point that was subselected from ls with mbr."
  [ls mbr point]
  (let [ls-mbr (:mbr ls)]
    (and (m/cartesian-covers-point? mbr point 0.00001)
         (m/cartesian-covers-point? ls-mbr point 0.00001)
         (s/point-on-segment? ls point))))

(defspec subselect-spec {:times 1000 :printer-fn print-subselect-failure}
  (for-all [ls sgen/line-segments
            mbr sgen/mbrs]
    (if-let [result (s/subselect ls mbr)]
      (let [{:keys [points line-segments]} result]
        (and (every? (partial valid-subselected-point? ls mbr) points)
             (every? (partial valid-subselected-line-segment? ls mbr) line-segments)
             ;; there must be at least one point or line segment if nil isn't returned.
             (or (seq points) (seq line-segments))))
      ;; No intersection
      (let [{:keys [point1 point2]} ls]
        (and ; The mbr shouldn't contain either line segment point
             (not (m/cartesian-covers-point? mbr point1))
             (not (m/cartesian-covers-point? mbr point2))
             ;; The line segment shouldn't intersect any of the mbr sides.
             (not (some #(s/intersection ls %)
                        (s/mbr->line-segments mbr))))))))
