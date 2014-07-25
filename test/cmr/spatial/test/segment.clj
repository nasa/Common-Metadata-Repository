(ns cmr.spatial.test.segment
  (:require [clojure.test :refer :all]
            [cmr.common.test.test-check-ext :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]

            ;; my code
            [cmr.spatial.math :refer :all]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.point :as p]
            [cmr.spatial.segment :as s]
            [cmr.spatial.derived :as d]

            [cmr.spatial.test.generators :as sgen]
            [cmr.spatial.validation :as v]
            [cmr.spatial.messages :as msg]))

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
        (m/covers-point? mbr point1)
        (m/covers-point? mbr point2)))))

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

(defspec line-segment->line-spec {:times 1000 :printer-fn sgen/print-failed-line-segments}
  (for-all [ls sgen/line-segments]
    (let [;; picked a larger amount to keep test fast since points may be very far apart
          densification-dist 5.0
          original-dist (s/distance ls)
          {:keys [point1 point2]} ls
          line (s/line-segment->line ls densification-dist)
          points (:points line)]
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

(defspec line-segment-intersection-spec {:times 10000 :printer-fn sgen/print-failed-line-segments}
  (for-all [ls1 sgen/line-segments
            ls2 sgen/line-segments]
    (let [mbr1 (:mbr ls1)
          mbr2 (:mbr ls2)
          point (s/intersection ls1 ls2)]
      (or (nil? point)
          (and (m/covers-point? mbr1 point)
               (m/covers-point? mbr2 point)
               (s/point-on-segment? ls1 point)
               (s/point-on-segment? ls2 point))))))

;; TODO add a specific example test of line segments that should intersect. The above could miss
;; line segments that must intersect.



