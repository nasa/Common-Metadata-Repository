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
  (when-not (relations/covers-br? shape from-mbr)
    (println (pr-str from-mbr)))
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
  (let [br (m/point->mbr from-point)]
    (when (relations/covers-br? shape br) ; Only continue if the seed point is in the shape.
      (lr-search [:north :south :east :west] shape br))))

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

(defn arc-ring-intersection-points
  "Returns the intersection points of the arc with the ring to use for finding lr search points
  Also includes the north and south pole if the ring covers them."
  [arc ring]
  (let [{:keys [mbr contains-north-pole contains-south-pole]} ring
        ;; Find the intersections of the arc through the ring. It will intersect an even number of times.
        intersections (mapcat (partial a/intersections arc) (:arcs ring))]
    ;; Add pole to intersections if it contains a pole
    (concat intersections
            (when contains-north-pole [p/north-pole])
            (when contains-south-pole [p/south-pole]))))

;; The default implementation will find intersections of an arc through the middle of the MBR of the
;; ring. It returns the midpoints of those intersections.
(defmethod ring->lr-search-points :default
  [ring]
  (let [{:keys [mbr contains-north-pole contains-south-pole]} ring
        vert-arc (mbr->vert-dividing-arc mbr)
        intersections (distinct
                        (arc-ring-intersection-points vert-arc ring))]

    ;; Between at least 2 of the points there will be a midpoint in the ring.
    ;; Create a list of test points from midpoints of all combinations of points.
    (distinct
      (map (fn [[p1 p2]]
             (a/midpoint (a/arc p1 p2)))
           (combo/combinations intersections 2)))))

(defmulti shape->lr-search-points
  "Returns points that may potentially be in the shape to use to search for a LR."
  (fn [shape]
    (type shape)))

(defmethod shape->lr-search-points Polygon
  [polygon]
  (let [boundary (poly/boundary polygon)
        holes (poly/holes polygon)
        mbr (relations/mbr polygon)]
    (if (empty? holes)
      (ring->lr-search-points boundary)

      ;; The holes may contain all of the lr search points we would have. We'll find intersections
      ;; through the middle of the MBR and the ring and all the holes. The midpoints of the
      ;; intersections should result in a point that will work.
      (let [vert-arc (mbr->vert-dividing-arc mbr)
            intersections (distinct (mapcat (partial arc-ring-intersection-points vert-arc)
                                            (:rings polygon)))]
        ;; Between at least 2 of the points there will be a midpoint in the polygon.
        ;; Create a list of test points from midpoints of all combinations of points.
        (distinct
          (map (fn [[p1 p2]]
                 (a/midpoint (a/arc p1 p2)))
               ;; Skip intersection combinations that are antipodal to each other.
               (filter (partial apply (complement p/antipodal?))
                       (combo/combinations intersections 2))))))))

(defmethod shape->lr-search-points Ring
  [ring]
  (ring->lr-search-points ring))

(defn find-lr
  "Finds the 'largest' interior rectangle (LR) of the shape. This is not the provably largest interior
  rectangle for a shape. It uses a simpler algorithm that works well for simple 4 point rings and
  less well for more points. It should always find a LR for any ring of arbitrary shape."
  ([shape]
   (find-lr shape true))
  ([shape not-found-is-error?]
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

     (if initial-lr
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
            (lr-search [:west] shape))

       (when not-found-is-error?
         (errors/internal-error!
           (str "Could not find lr from shape " (pr-str shape))))))))

