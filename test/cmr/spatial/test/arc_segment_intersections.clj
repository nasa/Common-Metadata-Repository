(ns cmr.spatial.test.arc-segment-intersections
   (:require [clojure.test :refer :all]
            [cmr.common.test.test-check-ext :as ext-gen :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]

            ;; my code
            [cmr.spatial.math :refer :all]
            [cmr.spatial.point :as p]
            [cmr.spatial.arc :as a]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.derived :as d]
            [cmr.spatial.segment :as s]
            [cmr.spatial.arc-segment-intersections :as asi]
            [cmr.spatial.test.generators :as sgen]
            [clojure.string :as str]))


(defn print-failure
  [type arc ls]
  (let [arc-ords (a/arc->ords arc)
        ls-ords (s/line-segment->ords ls)]
    (println (str "setArcOrdinates("
                  (str/join ", " arc-ords)
                  "); setLineOrdinates("
                  (str/join ", " ls-ords)
                  ");")))

  (sgen/print-failed-arc type arc)
  (sgen/print-failed-line-segments type ls))


(defspec arc-segment-intersections-spec {:times 1000 :printer-fn print-failure}
  (for-all [arc sgen/arcs
            ls sgen/line-segments]
    (let [ls (d/calculate-derived ls)
          intersections (asi/intersections ls arc)
          ls-mbr (:mbr ls)
          arc-mbrs (a/mbrs arc)]
      (if (empty? intersections)
        ;; They do not intersect
        ;; There should be no densified intersections
        (empty? (asi/intersection-with-densification ls arc))

        ;; They do intersect
        (and (every? (fn [point]
                       (let [lon (:lon point)
                             line-point (p/point lon (s/segment+lon->lat ls lon))
                             arc-point (a/point-at-lon arc lon)]
                         (approx= line-point arc-point 0.001)))
                     intersections)
             (every? (partial m/covers-point? ls-mbr) intersections)
             (every? (fn [point]
                       (some #(m/covers-point? % point) arc-mbrs))
                     intersections))
        ))))

;; TODO test arc intersection examples with cartesian lines that aren't valid arcs
;; - point 1 and 2 separated by a very large amount that would curve around opposite way in geodetic
;; - point 1 and 2 at north pole but different longitudes.
;; - point 1 and 2 from north pole to south pole (180 90 to -180 -90)
;; - point 1 and 2 from antimeridian 180 to -180


(comment


  (def arc  (cmr.spatial.arc/ords->arc -51.857142857142854 -83.99130434782609 -20.037037037037038 -68.97560975609755))
  (def ls (d/calculate-derived (cmr.spatial.segment/ords->line-segment -41.01298701298701 -86.98113207547169 40.01219512195122 63.992248062015506)))

  (require '[criterium.core :refer [with-progress-reporting bench]])
  (with-progress-reporting
      (bench
        (doall (asi/intersections ls arc))))


  (s/vertical? ls)
  (a/vertical? arc)
  (a/point-at-lon arc -180)

  (def intersections (asi/intersections ls arc))

  (and (every? (fn [point]
                 (let [lon (:lon point)
                       line-point (p/point lon (s/segment+lon->lat ls lon))
                       arc-point (a/point-at-lon arc lon)]
                   (println (pr-str line-point))
                   (println (pr-str arc-point))
                   (approx= line-point arc-point 0.0001)))
               intersections)
       (every? (partial m/covers-point? (:mbr ls)) intersections)
       (every? (fn [point]
                 (some #(m/covers-point? % point) (a/mbrs arc)))
               intersections))



  )