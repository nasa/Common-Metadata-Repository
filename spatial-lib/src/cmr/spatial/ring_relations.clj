(ns cmr.spatial.ring-relations
  "Contains functions on rings that are common to cartesian and geodetic rings."
  (:require
   [clojure.math.combinatorics :as combo]
   [cmr.common.util :as util]
   [cmr.spatial.arc-line-segment-intersections :as asi]
   [cmr.spatial.cartesian-ring :as cr]
   [cmr.spatial.conversion :as c]
   [cmr.spatial.geodetic-ring :as gr]
   [cmr.spatial.line-segment :as s]
   [cmr.spatial.line-string :as ls]
   [cmr.spatial.math :refer :all]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.point :as p]
   [primitive-math])
  (:import
   cmr.spatial.cartesian_ring.CartesianRing
   cmr.spatial.geodetic_ring.GeodeticRing
   cmr.spatial.point.Point
   cmr.spatial.mbr.Mbr))
(primitive-math/use-primitive-operators)

(defn ring
  "Creates a new ring in the coordinate system and points."
  [coordinate-system points]
  (case coordinate-system
    :geodetic (gr/ring points)
    :cartesian (cr/ring points)))

(defn ords->ring
  "Takes all arguments as coordinates for points, lon1, lat1, lon2, lat2, and creates a ring."
  [coordinate-system ords]
  (ring coordinate-system (p/ords->points ords (= :geodetic coordinate-system))))

(defn ring->ords [ring]
  (p/points->ords (:points ring)))

(defprotocol RingFunctions
  "Contains functions on the different ring types"
  (segments
    [ring]
    "Returns the line segments or arcs of the ring.")
  (covers-point?
    [ring point]
    "Returns true if the ring covers the point")
  (inside-out?
    [ring]
    "Returns true if the ring contains an area the opposite of what is allowed.")
  (coordinate-system
    [ring]
    "Returns the coordinate system of the ring.")
  (invert
    [ring]
    "Returns the ring with the points inverted"))

(extend-protocol RingFunctions
  CartesianRing
  (segments
    [ring]
    (:line-segments ring))
  (covers-point?
    [ring point]
    (cr/covers-point? ring point))
  (inside-out?
    [ring]
    (not= :counter-clockwise (cr/ring->winding ring)))
  (invert
    [ring]
    (cr/ring (reverse (:points ring))))
  (coordinate-system
    [_]
    :cartesian)

  GeodeticRing
  (segments
    [ring]
    (:arcs ring))
  (covers-point?
    [ring point]
    (gr/covers-point? ring point))
  (inside-out?
    [ring]
    (or (gr/point-order? ring)(gr/contains-both-poles? ring)))
  (invert
    [ring]
    (gr/ring (reverse (:points ring))))
  (coordinate-system
    [_]
    :geodetic))

(defn intersects-ring?
  "Returns true if the rings intersect each other."
  [r1 r2]
  (or
    ;; Do any of the line-segments intersect?
    ;; Performance enhancement: this should use the multiple arc intersection algorithm to avoid O(N^2) intersections
    (some (fn [[line1 line2]]
            (seq (asi/intersections line1 line2)))
          (for [ls1 (segments r1)
                ls2 (segments r2)]
            [ls1 ls2]))

    ;; Is ring 2 inside ring 1? Only one point check is required
    (covers-point? r1 (first (:points r2)))

    ;; Is ring 1 inside ring 2? Only one point check is required
    (covers-point? r2 (first (:points r1)))))

(defn covers-ring?
  "Returns true if the ring covers the other ring."
  [ring1 ring2]
  (let [ring1-lines (segments ring1)]
    (and (every? (partial covers-point? ring1) (:points ring2))
         (not-any? (fn [a1]
                     (some #(seq (asi/intersections a1 %)) ring1-lines))
                   (segments ring2)))))

(defn br-intersections
  "Returns a lazy sequence of the points where the ring lines intersect the br"
  [ring br]
  (when (m/intersects-br? (coordinate-system ring) (:mbr ring) br)
    (if (m/single-point? br)
      (let [point (p/point (:west br) (:north br))]
        (when (some #(asi/intersects-point? % point) (segments ring))
          [point]))
      (let [lines (segments ring)
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
         (m/covers-mbr? (coordinate-system ring) (:mbr ring) br)
         ;; The ring contains all the corner points of the br.
         (every? (partial covers-point? ring) corner-points)

         ;; The ring line-segments does not intersect bounding rectangle except on the points of the ring or br.
         (let [acceptable-points (set (concat (:points ring) corner-points))
               intersections (br-intersections ring br)]
           ;; Are there no intersections ...
           (or (empty? intersections)
               ;; Or is every intersection and acceptable point?
               (every? acceptable-points intersections))))))

(defn- lines-intersects-br-sides?
  "Returns truthy value if any of the lines intersects any of the br sides"
  [lines br]
  ;; Optimized to avoid iteration. There can be up to 6 sides if it crosses the antimeridian
  (let [[s1 s2 s3 s4 s5 s6] (s/mbr->line-segments br)]
    (loop [lines lines]
      (when-let [line (first lines)]
        (or (seq (asi/intersections line s1))
            (when s2 (seq (asi/intersections line s2)))
            (when s3 (seq (asi/intersections line s3)))
            (when s4 (seq (asi/intersections line s4)))
            (when s5 (seq (asi/intersections line s5)))
            (when s6 (seq (asi/intersections line s6)))
            (recur (rest lines)))))))

(defn intersects-br?
  "Returns true if the ring intersects the br"
  [ring ^Mbr br]
  (when (m/intersects-br? (coordinate-system ring) (:mbr ring) br)
    (if (m/single-point? br)
      (covers-point? ring (p/point (.west br) (.north br)))

      (or
       ;; Does the br cover any points of the ring?
       (if (= :geodetic (coordinate-system ring))
         (some #(m/geodetic-covers-point? br %) (:points ring))
         (some #(m/cartesian-covers-point? br %) (:points ring)))

       ;; Does the ring completely contain the br? We only need to check one point of the br
       (covers-point? ring (p/point (.west br) (.north br)))

       ;; Do any of the sides intersect?
       (lines-intersects-br-sides? (segments ring) br)))))

(defn intersects-line-string?
  "Returns true if the ring intersects the line"
  [ring line]

  (or ; line points are in ring
      (some (partial covers-point? ring) (:points line))

      ; line intersects ring arcs
      (some (fn [[segment1 segment2]]
              (seq (asi/intersections segment1 segment2)))
            (for [ls1 (:segments line)
                  ls2 (segments ring)]
              [ls1 ls2]))))

(defn covers-line-string?
  "Returns true if the ring covers the line string."
  [ring line]
  (and (every? (partial covers-point? ring) (:points line))
       (not-any? (fn [a1]
                   (some #(seq (asi/intersections a1 %))
                         (segments ring)))
                 (:segments line))))

(defn self-intersections
  "Returns the rings self intersections"
  [ring]
  (let [lines (segments ring)
        ;; Finds the indexes of the lines in the list to test intersecting together.
        ;; Works by finding all combinations and rejecting the lines would be sequential.
        ;; (The first and second line naturally touch on a shared point for instance.)
        line-test-indices (filter (fn [[^int n1 ^int n2]]
                                    (not (or ; Reject sequential indexes
                                             (= n1 (dec n2))
                                             ;; Reject the last line combined with first line.
                                             (and
                                               (zero? n1)
                                               (= n2 (dec (count lines)))))))
                                  (combo/combinations (range (count lines)) 2))]
    (mapcat (fn [[n1 n2]]
              (let [a1 (nth lines n1)
                    a2 (nth lines n2)]
                (asi/intersections a1 a2)))
            line-test-indices)))

