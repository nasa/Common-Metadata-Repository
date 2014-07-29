(ns cmr.spatial.cartesian-ring
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.math :refer :all]
            [cmr.common.util :as util]
            [primitive-math]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.conversion :as c]
            [cmr.spatial.segment :as s]
            [cmr.spatial.derived :as d]
            [clojure.math.combinatorics :as combo]
            [cmr.spatial.validation :as v]
            [cmr.spatial.messages :as msg]
            [cmr.spatial.arc-segment-intersections :as asi])
  (:import cmr.spatial.arc.Arc))
(primitive-math/use-primitive-operators)

(def external-point
  "Defines a point external to all cartesian rings. It works because it's outside the area of the
  earth."
  (p/point 181 91))

(defrecord CartesianRing
  [
   ;; The points that make up the ring. Points must be in counterclockwise order. The last point
   ;; must match the first point.
   points

   ;; Derived fields

   ;; A set of the unique points in the ring.
   ;; This should be used as opposed to creating a set from the points many times over which is expensive.
   point-set

   ;; Line segments of the ring
   line-segments

   ;; the minimum bounding rectangle
   mbr
   ])

(defn covers-point?
  "Determines if a ring covers the given point. The algorithm works by counting the number of times
  an arc between the point and a known external point crosses the ring. An even count means the point
  is external. An odd count means the point is inside the ring."
  [ring point]

  ;; Only do real intersection if the mbr covers the point.
  (when (mbr/covers-point? (:mbr ring) point)
    (if (some (:point-set ring) point)
      true ; The point is actually one of the rings points
      ;; otherwise we'll do the real intersection algorithm
      (let [;; Create the test segment
            crossing-line (s/line-segment point external-point)
            ;; Find all the points the line passes through
            intersections (filter identity
                                  (map #(s/intersection % crossing-line) (:line-segments ring)))
            ;; Round the points. If the crossing arc passes through a point on the ring the
            ;; intersection algorithm will result in two very, very close points. By rounding to
            ;; within an acceptable range they'll be seen as the same point.
            intersections (set (map (partial p/round-point 5) intersections))]
        (or (odd? (count intersections))
            ;; if the point itself is one of the intersections then the ring covers it
            (intersections point))))))

(defn intersects-ring?
  "Returns true if the rings intersect each other."
  [r1 r2]
  (or
    ;; Do any of the line-segments intersect?
    ;; TODO performance improvement: this should use the multiple arc intersection algorithm to avoid O(N^2) intersections
    (some (fn [[line1 line2]]
            (seq (asi/intersections line1 line2)))
          (for [ls1 (:line-segments r1)
                ls2 (:line-segments r2)]
            [ls1 ls2]))

    ;; Are any of the points in ring 2 inside ring 1?
    (some #(covers-point? r1 %) (:points r2))

    ;; Are any of the points in ring 1 inside ring 2?
    (some #(covers-point? r2 %) (:points r1))))

(defn br-intersections
  "Returns a lazy sequence of the points where the ring lines intersect the br"
  [ring br]
  (when (mbr/intersects-br? (:mbr ring) br)
    (let [line-segments (:line-segments ring)
          mbr-lines (s/mbr->line-segments (:mbr ring))]
      (filter identity
              (map (partial apply s/intersection)
                   (for [ls1 line-segments
                         ls2 mbr-lines]
                     [ls1 ls2]))))))

(defn covers-br?
  "Returns true if the ring covers the entire br"
  [ring br]
  (let [corner-points (mbr/corner-points br)]
    (and ;; The rings mbr covers the br
         (mbr/covers-mbr? (:mbr ring) br)
         ;; The ring contains all the corner points of the br.
         (every? (partial covers-point? ring) corner-points)

         ;; The ring line-segments does not intersect bounding rectangle except on the points of the ring or br.
         (let [acceptable-points (set (concat (:points ring) corner-points))
               intersections (br-intersections ring br)]
           ;; Are there no intersections ...
           (or (empty? intersections)
               ;; Or is every intersection and acceptable point?
               (every? acceptable-points intersections))))))

(defn covers-ring?
  "Returns true if the ring covers the other ring."
  [ring1 ring2]
  (let [ring1-line-segments (:line-segments ring1)]
    (and (every? (partial covers-point? ring1) (:points ring2))
         (not-any? (fn [a1]
                     (some #(seq (asi/intersections a1 %)) ring1-line-segments))
                   (:line-segments ring2)))))

(defn intersects-br?
  "Returns true if the ring intersects the br"
  [ring br]
  (when (mbr/intersects-br? (:mbr ring) br)

    (or
      ;; Does the br cover any points of the ring?
      (some (partial mbr/covers-point? br) (:points ring))
      ;; Does the ring contain any points of the br?
      (some (partial covers-point? ring) (mbr/corner-points br))

      ;; Do any of the sides intersect?
      (let [line-segments (:line-segments ring)
            mbr-lines (s/mbr->line-segments (:mbr ring))]
        (seq (filter identity
                     (map (partial apply s/intersection)
                          (for [ls1 line-segments
                                ls2 mbr-lines]
                            [ls1 ls2]))))))))

(defn self-intersections
  "Returns the rings self intersections"
  [ring]
  (let [line-segments (:line-segments ring)
        ;; Finds the indexes of the line-segments in the list to test intersecting together.
        ;; Works by finding all combinations and rejecting the line-segments that would be sequential.
        ;; (The first and second arc naturally touch on a shared point for instance.)
        line-test-indices (filter (fn [[^int n1 ^int n2]]
                                    (not (or ; Reject sequential indexes
                                             (= n1 (dec n2))
                                             ;; Reject the last arc combined with first arc.
                                             (and
                                               (= n1 0)
                                               (= n2 (dec (count line-segments)))))))
                                  (combo/combinations (range (count line-segments)) 2))]
    (filter identity (map (fn [[n1 n2]]
                            (let [ls1 (nth line-segments n1)
                                  ls2 (nth line-segments n2)]
                              (s/intersection ls1 ls2)))
                          line-test-indices))))

(defn ring
  "Creates a new ring with the given points. If the other fields of a ring are needed. The
  calculate-derived function should be used to populate it."
  [points]
  (->CartesianRing points nil nil nil nil))

(defn ring->line-segments
  "Determines the line-segments from the points in the ring."
  [^CartesianRing ring]
  (or (.line_segments ring)
      (s/points->line-segments (.points ring))))

(defn ring->mbr
  "Determines the mbr from the points in the ring."
  [^CartesianRing ring]
  (or (.mbr ring)
      (let [line-segments (ring->line-segments ring)]
        (->> line-segments (map :mbr) (reduce #(mbr/union %1 %2 false))))))

(extend-protocol d/DerivedCalculator
  cmr.spatial.cartesian_ring.CartesianRing
  (calculate-derived
    [^CartesianRing ring]
    (if (.line_segments ring)
      ring
      (as-> ring ring
            (assoc ring :point-set (set (:points ring)))
            (assoc ring :line-segments (ring->line-segments ring))
            (assoc ring :mbr (ring->mbr ring))))))

(defn ords->ring
  "Takes all arguments as coordinates for points, lon1, lat1, lon2, lat2, and creates a ring."
  [& ords]
  (ring (apply p/ords->points ords)))

(defn ring->ords [ring]
  (p/points->ords (:points ring)))

