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

;; TODO what tests can I write?
;; arc and line segment intersection
;; line segment and line segment intersection
;; line segment validation

(defn valid-double?
  [^double v]
  (and (not (Double/isInfinite v))
       (not (Double/isNaN v))))

(defspec line-segment-calculate-derived-spec {:times 1000 :printer-fn sgen/print-failed-line-segment}
  (for-all [ls sgen/line-segments]
    (let [ls (d/calculate-derived ls)
          {:keys [b m mbr point1 point2]} ls]
      (and
        (or (s/vertical? ls)
            (and
              (valid-double? b)
              (valid-double? m)))

        (not (m/crosses-antimeridian? mbr))
        (m/covers-point? mbr point1)
        (m/covers-point? mbr point2)))))

(defspec segment+lon->lat-spec {:times 1000 :printer-fn sgen/print-failed-line-segment}
  (for-all [ls (gen/such-that (complement s/vertical?) sgen/line-segments)]
    (let [ls (d/calculate-derived ls)
          {:keys [point1 point2]} ls]
      (and
        (approx= (:lat point1) (s/segment+lon->lat ls (:lon point1)))
        (approx= (:lat point2) (s/segment+lon->lat ls (:lon point2)))))))

(defspec segment+lat->lon-spec {:times 1000 :printer-fn sgen/print-failed-line-segment}
  (for-all [ls (gen/such-that (complement s/horizontal?) sgen/line-segments)]
    (let [ls (d/calculate-derived ls)
          {:keys [point1 point2]} ls]
      (and
        (approx= (:lon point1) (s/segment+lat->lon ls (:lat point1)))
        (approx= (:lon point2) (s/segment+lat->lon ls (:lat point2)))))))

(defspec line-segment->line-spec {:times 1000 :printer-fn sgen/print-failed-line-segment}
  (for-all [ls sgen/line-segments]
    (let [ls (d/calculate-derived ls)
          ;; picked a larger amount to keep test fast since points may be very far apart
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

(comment
  (def ls (d/calculate-derived (cmr.spatial.segment/ords->line-segment -100.0 -27.0 -1.0 -27.0)))

  (s/segment+lat->lon ls -27.0)

  (s/line-segment->line ls 5.0)

  (let [   ;; picked a larger amount to keep test fast since points may be very far apart
        densification-dist 5.0
        original-dist (s/distance ls)
        {:keys [point1 point2]} ls
        line (s/line-segment->line ls densification-dist)
        points (:points line)]
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
        (<= ^double (last distances) densification-dist))))



  (s/segment+lon->lat ls -1)

  (#{1 2 3} 2)

  )


;; TODO test arc intersection examples with cartesian lines that aren't valid arcs
;; - point 1 and 2 separated by a very large amount that would curve around opposite way in geodetic
;; - point 1 and 2 at north pole but different longitudes.
;; - point 1 and 2 from north pole to south pole (180 90 to -180 -90)
;; - point 1 and 2 from antimeridian 180 to -180

; (defspec arc-and-line-segment-intersection-spec 1000
;   (for [arc sgen/arcs
;         ls sgen/line-segments]
;     ))

