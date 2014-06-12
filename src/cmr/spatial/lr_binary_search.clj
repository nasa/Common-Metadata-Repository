(ns cmr.spatial.lr-binary-search
  "Prototype code that finds the largest interior rectangle of a ring."
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.ring :as r]
            [cmr.spatial.math :refer :all]
            [primitive-math]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.conversion :as c]
            [cmr.common.services.errors :as errors]
            [cmr.spatial.arc :as a]
            [cmr.spatial.derived :as d]
            [clojure.math.combinatorics :as combo]
            [pjstadig.assertions :as pj]
            [cmr.common.util :as util]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.relations :as relations])
  (:import cmr.spatial.mbr.Mbr
           cmr.spatial.ring.Ring
           cmr.spatial.polygon.Polygon))
(primitive-math/use-primitive-operators)

(def ^:const ^long max-search-depth
  "The number of times the binary search can iterate before we choose the best option. If it's covering
  a space 90 tall or wide after 10 recursions it will 5.625 degrees wide (90 / 2^4). We do more than one search
  in different directions so that get's shrunk multiple times past that."
  4)

(defn mid-br
  "Returns an mbr midway between inner and outer mbrs growing the mbr in the directions given.
  Directions should be a set of keywords of north, south, east, and west of directions that the MBR
  can be modified. If a direction is not specified the inner mbr value will be returned"
  [directions inner outer]
  (let [{^double ni :north ^double si :south ^double ei :east ^double wi :west} inner
        {^double no :north ^double so :south ^double eo :east ^double wo :west} outer
        w (if (:west directions) (mid-lon wo wi) wi)
        n (if (:north directions) (mid ni no) ni)
        e (if (:east directions) (mid-lon ei eo) ei)
        s (if (:south directions) (mid si so) si)]
    (m/mbr w n e s)))

(defn lr-search
  "Searches for an LR from the given point in the shape. The LR will grow the coordinates in the given
  directions. Directions should be a sequence of keywords i.e. :north, :south, :east, :west"
  [directions shape from-mbr]
  (pj/assert (relations/covers-br? shape from-mbr))
  (util/binary-search
    ;; min value
    from-mbr
    ;; max value
    (relations/mbr shape)
    ;; Can compute middle value for binary search
    (partial mid-br (set directions))
    ;; Determines if binary search is done.
    (fn [current-br inner-br outer-br ^long depth]
      (let [current-in-shape (relations/covers-br? shape current-br)]
        (if (> depth max-search-depth)
          ;; Exceeded the recursion depth. Take our best choice
          (if current-in-shape current-br inner-br)
          (if current-in-shape :less-than :greater-than))))))

(defn- even-lr-search
  "Searches for an LR from the given point in the shape. The LR will grown evently from the point
  given with the same shape as the outer MBR."
  [shape from-point]
  (when (relations/covers-point? shape from-point) ; Only continue if the seed point is in the shape.
    (let [{:keys [lon lat]} from-point
          minv (m/mbr lon lat lon lat)]
      (lr-search [:north :south :east :west] shape minv))))

(defn mbr->vert-dividing-arc
  "Returns an arc dividing the MBR in two vertically"
  [^Mbr mbr]
  (let [w (.west mbr)
        n (.north mbr)
        e (.east mbr)
        s (.south mbr)
        mid (mid-lon w e)]
    (a/arc (p/point mid n) (p/point mid s))))

(defn ring->triangles
  "Converts a ring into a series of smaller triangles. Triangles do not necessarily represent
  the area of the ring."
  [ring]
  (let [points (:points ring)]
    (if (= 4 (count points))
      ;; Already a triangle
      [ring]
      (map (fn [[p1 p2 p3]]
             (let [tri (d/calculate-derived (r/ring [p1 p2 p3 p1]))]
               (if (r/contains-both-poles? tri)
                 (d/calculate-derived (r/invert tri))
                 tri)))
           ;; If contains two poles reverse it
           (partition 3 1 (concat points [(second points)]))))))

(defn triangle->point
  "Returns a point from a ring that is a triangle. Works be creating an arc from the midpoint of one
  arc to its opposite point."
  [ring]
  (a/midpoint (a/arc (first (:points ring))
                     (a/midpoint (second (:arcs ring))))))

(defmulti ring->lr-search-points
  "Returns points that may potentially be in the ring to use to search for a LR."
  (fn [ring]
    (let [points (:points ring)]
      (cond
        (some p/is-pole? points) :on-pole
        :else :default))))

;; If the ring is on a pole we divide up the ring into triangles and return points inside the triangles.
(defmethod ring->lr-search-points :on-pole
  [ring]
  (map triangle->point (ring->triangles ring)))

;; The default implementation will find intersections of an arc through the middle of the MBR of the
;; ring. It returns the midpoints of those intersections.
(defmethod ring->lr-search-points :default
  [ring]
  (let [{:keys [mbr contains-north-pole contains-south-pole]} ring
        vert-arc (mbr->vert-dividing-arc mbr)

        ;; Find the intersections of the arc through the ring. It will intersect an even number of times.
        intersections (mapcat (partial a/intersections vert-arc) (:arcs ring))

        ;; Add pole to intersections if it contains a pole
        intersections (set (concat intersections
                                   (when contains-north-pole [p/north-pole])
                                   (when contains-south-pole [p/south-pole])))]
    ;; Between at least 2 of the points there will be a midpoint in the ring.
    ;; Create a list of test points from midpoints of all combinations of points.
    (map (fn [[p1 p2]]
           (a/midpoint (a/arc p1 p2)))
         (combo/combinations intersections 2))))

(defmulti shape->lr-search-points
  "Returns points that may potentially be in the shape to use to search for a LR."
  (fn [shape]
    (type shape)))

(defmethod shape->lr-search-points Polygon
  [polygon]
  (ring->lr-search-points (poly/boundary polygon)))

(defmethod shape->lr-search-points Ring
  [ring]
  (ring->lr-search-points ring))

(defn find-lr
  "Finds the 'largest' interior rectangle (LR) of the shape. This is not the provably largest interior
  rectangle for a shape. It uses a simpler algorithm that works well for simple 4 point rings and
  less well for more points. It should always find a LR for any ring of arbitrary shape."
  [shape]
  (let [shape (d/calculate-derived shape)
        mbr (relations/mbr shape)
        contains-north-pole (relations/contains-north-pole? shape)
        contains-south-pole (relations/contains-south-pole? shape)
        initial-lr (or
                     ;; Find an LR from the center point of the shape
                     (even-lr-search shape (m/center-point mbr))
                     ;; Return the first LR that we can find using a test point
                     (first (filter identity
                                    (map (partial even-lr-search shape)
                                         (shape->lr-search-points shape)))))]
    (when-not initial-lr
      (errors/internal-error!
        (str "Could not find lr from shape " (pr-str shape))))

    ;; Grow LR in all directions
    (->> initial-lr
         ;; Grow corners
         (lr-search [:west :north] shape)
         (lr-search [:north :east] shape)
         (lr-search [:east :south] shape)
         (lr-search [:south :west] shape)
         ;; Grow edges
         (lr-search [:north] shape)
         (lr-search [:east] shape)
         (lr-search [:south] shape)
         (lr-search [:west] shape))))

