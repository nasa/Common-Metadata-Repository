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
            [cmr.spatial.line-segment :as s]
            [cmr.spatial.arc-line-segment-intersections :as asi]
            [cmr.spatial.test.generators :as sgen]
            [clojure.string :as str]
            [cmr.spatial.dev.viz-helper :as viz-helper]))


(defn print-failure
  [type arc ls]
  (sgen/print-failed-arc type arc)
  (sgen/print-failed-line-segments type ls))


(defn valid-intersection-point?
  [arc ls point]
  (or (let [lon (:lon point)
            line-point (if (s/vertical? ls)
                         (if (s/point-on-segment? ls point)
                           point
                           ;; Failure case but return something to help debugging
                           :point-not-on-vertical-segment)
                         (p/point lon (s/segment+lon->lat ls lon)))
            arc-points (if (a/vertical? arc)
                         (a/points-at-lat arc (:lat point))
                         [(a/point-at-lon arc lon)])]
        (some #(approx= line-point % 0.01) arc-points))
      (and (s/point-on-segment? ls point)
           (a/point-on-arc? arc point))))

(defspec arc-segment-intersections-spec {:times 100 :printer-fn print-failure}
  (for-all [arc sgen/arcs
            ls sgen/line-segments]
    (let [intersections (asi/intersections ls arc)
          ls-mbr (:mbr ls)
          arc-mbrs (a/mbrs arc)]
      (if (empty? intersections)
        ;; They do not intersect
        ;; There should be no densified intersections
        (empty? (asi/line-segment-arc-intersections-with-densification ls arc [m/whole-world]))

        ;; They do intersect
        (and (every? (partial valid-intersection-point? arc ls) intersections)
             (every? (partial m/geodetic-covers-point? ls-mbr) intersections)
             (every? (fn [point]
                       (some #(m/geodetic-covers-point? % point) arc-mbrs))
                     intersections))))))


(deftest example-arc-line-segment-intersections
  (are [ls-ords arc-ords intersection-ords]
       (let [intersection-points (apply p/ords->points intersection-ords)
             intersections (asi/intersections (apply s/ords->line-segment ls-ords)
                                              (apply a/ords->arc arc-ords))]
         (approx= intersection-points intersections))

       ;; T intersection (vertical arc)
       [2 5 4 5] [3 5, 3 -5] [3 5]

       ;; line segment at north pole
       [10 90 30 90] [0 90 10 85] [0 90]

       ;; line segment at south pole
       [10 -90 30 -90] [0 -90 10 -85] [0 -90]

       ;; line segment along antimeridian
       [180 10 180 20] [175 15 -175 15] [180.0 15.0547]

       ;; arc starts on south pole
       [0 0 -25 -30] [85 -90 -14 -7] [-14 -16.8]
       ;; arc ends on south pole
       [0 0 -25 -30] [-14 -7 85 -90] [-14 -16.8]

       ;; arc ends on south pole and they intersect there
       [-117.0 -90.0 27.0 51.0] [-14.0 -36.0 1.0 -90.0] [0 -90]

       ;; arc starts on north pole
       [0 0 -25 -30] [85 90 -14 -20] [-14 -16.8]
       ;; arc ends on north pole
       [0 0 -25 -30] [-14 -20 85 90] [-14 -16.8]))


(comment

(do
  (def arc  (cmr.spatial.arc/ords->arc -51.857142857142854 -83.99130434782609 -20.037037037037038 -68.97560975609755))
  (def ls (cmr.spatial.line-segment/ords->line-segment -41.01298701298701 -86.98113207547169 40.01219512195122 63.992248062015506))

  (def intersections (asi/intersections ls arc))

  (s/subselect ls (:mbr1 arc))

  )


  (viz-helper/clear-geometries)
  (viz-helper/add-geometries [ls arc])
  (viz-helper/add-geometries intersections)

  (viz-helper/add-geometries [ls (:mbr1 arc)])
  (viz-helper/add-geometries [(s/subselect ls (:mbr1 arc))])

  (viz-helper/add-geometries (s/mbr->line-segments (:mbr1 arc)))
  (viz-helper/add-geometries [(cmr.spatial.point/point 55.0 -54.353923205342234)
                              (cmr.spatial.point/point -80.47701149425286 -15.0)
                              (cmr.spatial.point/point -113.0 -5.552587646076795)])



  (require '[criterium.core :refer [with-progress-reporting bench]])
  (with-progress-reporting
      (bench
        (doall (asi/intersections ls arc))))


  (s/vertical? ls)
  (a/vertical? arc)
  (a/point-at-lon arc -180)


  (and (every? (fn [point]
                 (let [lon (:lon point)
                       line-point (p/point lon (s/segment+lon->lat ls lon))
                       arc-point (a/point-at-lon arc lon)]
                   (println (pr-str line-point))
                   (println (pr-str arc-point))
                   (approx= line-point arc-point 0.001)))
               intersections)
       (every? (partial m/covers-point? (:mbr ls)) intersections)
       (every? (fn [point]
                 (some #(m/covers-point? % point) (a/mbrs arc)))
               intersections))



  )