(ns cmr.spatial.ring-relations
  "Contains functions on rings that are common to cartesian and geodetic rings."
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.math :refer :all]
            [cmr.common.util :as util]
            [primitive-math]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.conversion :as c]
            [cmr.spatial.segment :as s]
            [cmr.spatial.arc-segment-intersections :as asi]
            [cmr.spatial.cartesian-ring :as cr]
            [cmr.spatial.point :as p]
            [cmr.spatial.geodetic-ring :as gr])
  (:import cmr.spatial.cartesian_ring.CartesianRing
           cmr.spatial.geodetic_ring.GeodeticRing
           cmr.spatial.point.Point))
(primitive-math/use-primitive-operators)

(defprotocol RingFunctions
  "Contains functions on the different ring types"
  (lines
    [ring]
    "Returns the line segments or arcs of the ring.")
  (covers-point?
    [ring point]
    "Returns true if the ring covers the point"))

(extend-protocol RingFunctions
  CartesianRing
  (lines
    [ring]
    (:line-segments ring))
  (covers-point?
    [ring point]
    (cr/covers-point? ring point))

  GeodeticRing
  (lines
    [ring]
    (:arcs ring))
  (covers-point?
    [ring point]
    (gr/covers-point? ring point)))

(defn intersects-ring?
  "Returns true if the rings intersect each other."
  [r1 r2]
  (or
    ;; Do any of the line-segments intersect?
    ;; TODO performance improvement: this should use the multiple arc intersection algorithm to avoid O(N^2) intersections
    (some (fn [[line1 line2]]
            (seq (asi/intersections line1 line2)))
          (for [ls1 (lines r1)
                ls2 (lines r2)]
            [ls1 ls2]))

    ;; Are any of the points in ring 2 inside ring 1?
    (some #(covers-point? r1 %) (:points r2))

    ;; Are any of the points in ring 1 inside ring 2?
    (some #(covers-point? r2 %) (:points r1))))

(defn covers-ring?
  "Returns true if the ring covers the other ring."
  [ring1 ring2]
  (let [ring1-lines (lines ring1)]
    (and (every? (partial covers-point? ring1) (:points ring2))
         (not-any? (fn [a1]
                     (some #(seq (asi/intersections a1 %)) ring1-lines))
                   (lines ring2)))))

(defn br-intersections
  "Returns a lazy sequence of the points where the ring lines intersect the br"
  [ring br]
  (when (m/intersects-br? (:mbr ring) br)
    (if (m/single-point? br)
      (let [point (p/point (:west br) (:north br))]
        (when (some #(asi/intersects-point? % point) (lines ring))
          [point]))
      (let [lines (lines ring)
            mbr-lines (s/mbr->line-segments br)]
        (mapcat (partial apply asi/intersections)
                (for [line1 lines
                      line2 mbr-lines]
                  [line1 line2]))))))

(defn covers-br?
  "Returns true if the ring covers the entire br"
  [ring br]
  (let [corner-points (m/corner-points br)]
    (and ;; The rings mbr covers the br
         (m/covers-mbr? (:mbr ring) br)
         ;; The ring contains all the corner points of the br.
         (every? (partial covers-point? ring) corner-points)

         ;; The ring line-segments does not intersect bounding rectangle except on the points of the ring or br.
         (let [acceptable-points (set (concat (:points ring) corner-points))
               intersections (br-intersections ring br)]
           ;; Are there no intersections ...
           (or (empty? intersections)
               ;; Or is every intersection and acceptable point?
               (every? acceptable-points intersections))))))

(defn intersects-br?
  "Returns true if the ring intersects the br"
  [ring br]
  (when (m/intersects-br? (:mbr ring) br)
    (if (m/single-point? br)
      (covers-point? ring (p/point (:west br) (:north br)))

      (or
        ;; Does the br cover any points of the ring?
        (some (partial m/covers-point? br) (:points ring))
        ;; Does the ring contain any points of the br?
        (some (partial covers-point? ring) (m/corner-points br))

        ;; Do any of the sides intersect?
        (let [lines (lines ring)
              mbr-lines (s/mbr->line-segments (:mbr ring))]
          (seq (mapcat (partial apply asi/intersections)
                       (for [ls1 lines
                             ls2 mbr-lines]
                         [ls1 ls2]))))))))

