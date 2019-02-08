(ns cmr.spatial.test.arc-segment-intersections
   (:require
    [clojure.string :as str]
    [clojure.test :refer :all]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :refer [for-all]]
    [cmr.common.test.test-check-ext :as ext-gen :refer [defspec]]
    [cmr.spatial.arc :as a]
    [cmr.spatial.arc-line-segment-intersections :as asi]
    [cmr.spatial.line-segment :as s]
    [cmr.spatial.math :refer :all]
    [cmr.spatial.mbr :as m]
    [cmr.spatial.point :as p]
    [cmr.spatial.test.generators :as sgen]))

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
       (let [intersection-points (p/ords->points intersection-ords)
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
