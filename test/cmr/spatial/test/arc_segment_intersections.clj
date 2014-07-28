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
            [cmr.spatial.segment :as s]
            [cmr.spatial.arc-segment-intersections :as asi]
            [cmr.spatial.test.generators :as sgen]
            [clojure.string :as str]
            [cmr.spatial.dev.viz-helper :as viz-helper]))


(defn print-failure
  [type arc ls]
  (sgen/print-failed-arc type arc)
  (sgen/print-failed-line-segments type ls))


(defspec arc-segment-intersections-spec {:times 1000 :printer-fn print-failure}
  (for-all [arc sgen/arcs
            ls sgen/line-segments]
    (let [intersections (asi/intersections ls arc)
          ls-mbr (:mbr ls)
          arc-mbrs (a/mbrs arc)]
      (if (empty? intersections)
        ;; They do not intersect
        ;; There should be no densified intersections
        (empty? (asi/intersection-with-densification ls arc [m/whole-world]))

        ;; They do intersect
        (and (every? (fn [point]
                       (let [lon (:lon point)
                             line-point (p/point lon (s/segment+lon->lat ls lon))
                             arc-point (a/point-at-lon arc lon)]
                         (approx= line-point arc-point 0.01)))
                     intersections)
             (every? (partial m/covers-point? ls-mbr) intersections)
             (every? (fn [point]
                       (some #(m/covers-point? % point) arc-mbrs))
                     intersections))))))

(comment

(do
  (def arc  (cmr.spatial.arc/ords->arc -51.857142857142854 -83.99130434782609 -20.037037037037038 -68.97560975609755))
  (def ls (cmr.spatial.segment/ords->line-segment -41.01298701298701 -86.98113207547169 40.01219512195122 63.992248062015506))

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