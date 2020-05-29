(ns cmr.spatial.lr-binary-search
  "Prototype code that finds the largest interior rectangle of a ring."
  (:require
    [clojure.math.combinatorics :as combo]
    [cmr.common.log :refer (warn)]
    [cmr.common.services.errors :as errors]
    [cmr.common.util :as util]
    [cmr.spatial.arc :as a]
    [cmr.spatial.arc-line-segment-intersections :as asi]
    [cmr.spatial.conversion :as c]
    [cmr.spatial.derived :as d]
    [cmr.spatial.geodetic-ring :as gr]
    [cmr.spatial.line-segment :as s]
    [cmr.spatial.math :refer :all]
    [cmr.spatial.mbr :as m]
    [cmr.spatial.point :as p]
    [cmr.spatial.polygon :as poly]
    [cmr.spatial.relations :as relations]
    [cmr.spatial.ring-relations :as rr]
    [pjstadig.assertions :as pj]
    [primitive-math])
  (:import
    (cmr.spatial.cartesian_ring CartesianRing)
    (cmr.spatial.geodetic_ring GeodeticRing)
    (cmr.spatial.mbr Mbr)
    (cmr.spatial.polygon Polygon)))
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

(defn even-lr-search
  "Searches for an LR from the given point in the shape. The LR will grown evently from the point
  given with the same shape as the outer MBR."
  [shape from-point]
  (let [br (m/point->mbr from-point)]
    (when (relations/covers-br? shape br) ; Only continue if the seed point is in the shape.
      (lr-search [:north :south :east :west] shape br))))

(defn mbr->vert-dividing-line
  "Returns an arc dividing the MBR in two vertically"
  [^Mbr mbr]
  (let [w (.west mbr)
        n (.north mbr)
        e (.east mbr)
        s (.south mbr)
        mid (mid-lon w e)]
    (s/line-segment (p/point mid n) (p/point mid s))))

(defn ring->triangles
  "Converts a ring into a series of smaller triangles. Triangles do not necessarily represent
  the area of the ring."
  [ring]
  (let [points (:points ring)
        coord-sys (rr/coordinate-system ring)]
    (if (= 4 (count points))
      ;; Already a triangle
      [ring]
      (map (fn [[p1 p2 p3]]
             (let [tri (d/calculate-derived (rr/ring coord-sys [p1 p2 p3 p1]))]
               (if (rr/inside-out? tri)
                 (d/calculate-derived (rr/invert tri))
                 tri)))
           ;; If contains two poles reverse it
           (partition 3 1 (concat points [(second points)]))))))

(defn triangle->point
  "Returns a point from a ring that is a triangle. Works be creating an arc from the midpoint of one
  arc to its opposite point."
  [ring]
  (a/midpoint (a/arc (first (:points ring))
                     (a/midpoint (second (rr/segments ring))))))

(defn ring-point-on-pole?
  "Returns true if this is a geodetic coordinate system at the ring has a poitn on the pole"
  [ring]
  (let [points (:points ring)]
    (and (= :geodetic (rr/coordinate-system ring))
         (some p/is-pole? points))))

(defmulti ring->lr-search-points
  "Returns points that may potentially be in the ring to use to search for a LR."
  (fn [ring]
    (if (ring-point-on-pole? ring)
      :on-pole
      :default)))

;; If the ring is on a pole we divide up the ring into triangles and return points inside the triangles.
(defmethod ring->lr-search-points :on-pole
  [ring]
  (map triangle->point (ring->triangles ring)))

(defn line-ring-intersection-points
  "Returns the intersection points of the line with the ring to use for finding lr search points
  Also includes the north and south pole if the ring covers them."
  [arc ring]
  (let [{:keys [mbr contains-north-pole contains-south-pole]} ring
        ;; Find the intersections of the arc through the ring. It will intersect an even number of times.
        intersections (mapcat (partial asi/intersections arc) (rr/segments ring))]
    ;; Add pole to intersections if it contains a pole
    (concat intersections
            (when contains-north-pole [p/north-pole])
            (when contains-south-pole [p/south-pole]))))

(defn distinct-points
  "Lazily returns a set of distinct points. Works by rounding the points so that if they are very
  close they will be considered equal"
  [points]
  (distinct (map (partial p/round-point 11) points)))

;; The default implementation will find intersections of an arc through the middle of the MBR of the
;; ring. It returns the midpoints of those intersections.
(defmethod ring->lr-search-points :default
  [ring]
  (let [{:keys [mbr]} ring
        vert-line (mbr->vert-dividing-line mbr)
        intersections (line-ring-intersection-points vert-line ring)

        ;; Handle cases where the ring mbr is the width of the entire world
        intersections (if (and (= -180.0 (:west mbr))
                               (= 180.0 (:east mbr)))
                        ;; The vertical arc above will be on the prime meridian. Try the antimeridian as well.
                        (distinct-points
                          (concat intersections
                                  (line-ring-intersection-points
                                    (mbr->vert-dividing-line (assoc mbr :west 179.0 :east -179.0))
                                    ring)))
                        (distinct-points intersections))]

    ;; Between at least 2 of the points there will be a midpoint in the ring.
    ;; Create a list of test points from midpoints of all combinations of points.
    (distinct-points
      (map (fn [[p1 p2]]
             (a/midpoint (a/arc p1 p2)))
           (combo/combinations intersections 2)))))

(defmulti shape->lr-search-points
  "Returns points that may potentially be in the shape to use to search for a LR."
  type)

(defmulti polygon->lr-search-points
  (fn [polygon]
    (let [point-on-pole? (ring-point-on-pole? (poly/boundary polygon))
          has-holes? (seq (poly/holes polygon))]
      (cond
        (and point-on-pole? has-holes?) :on-pole-with-holes
        has-holes? :has-holes
        :else :default))))

(defmethod polygon->lr-search-points :default
  [polygon]
  (ring->lr-search-points (poly/boundary polygon)))

(defmethod polygon->lr-search-points :has-holes
  [polygon]
  ;; The holes may contain all of the lr search points we would have. We'll find intersections
  ;; through the middle of the MBR and the ring and all the holes. The midpoints of the
  ;; intersections should result in a point that will work.
  (let [mbr (relations/mbr polygon)
        vert-line (mbr->vert-dividing-line mbr)
        intersections (distinct-points (mapcat (partial line-ring-intersection-points vert-line)
                                               (:rings polygon)))]
    ;; Between at least 2 of the points there will be a midpoint in the polygon.
    ;; Create a list of test points from midpoints of all combinations of points.
    (distinct-points
      (map (fn [[p1 p2]]
             (a/midpoint (a/arc p1 p2)))
           ;; Skip intersection combinations that are antipodal to each other.
           (filter (partial apply (complement p/antipodal?))
                   (combo/combinations intersections 2))))))

(defmethod polygon->lr-search-points :on-pole-with-holes
  [polygon]
  ;; If a polygon has holes and a point on the pole we'll be be able to find a point in the polygon
  ;; by searching for a midpoint between the points of the hole and the point on the pole.
  (let [boundary (poly/boundary polygon)
        pole-point (first (filter p/is-pole? (:points boundary)))
        hole-points (mapcat :points (poly/holes polygon))]
    (distinct-points
      (map (fn [hole-point]
             (a/midpoint (a/arc pole-point hole-point)))
           hole-points))))


(defmethod shape->lr-search-points Polygon
  [polygon]
  (polygon->lr-search-points polygon))

(defmethod shape->lr-search-points GeodeticRing
  [ring]
  (ring->lr-search-points ring))

(defmethod shape->lr-search-points CartesianRing
  [ring]
  (ring->lr-search-points ring))

(defn find-lr
  "Finds the 'largest' interior rectangle (LR) of the polygon. This is not the provably largest interior
  rectangle for a polygon. It uses a simpler algorithm that works well for simple 4 point rings and
  less well for more points. It should always find a LR for any ring of arbitrary polygon."
  ([polygon]
   (find-lr polygon false))
  ([polygon not-found-is-error?]
   (let [polygon (d/calculate-derived polygon)
         mbr (relations/mbr polygon)
         initial-lr (or
                      ;; Find an LR from the center point of the polygon
                      (even-lr-search polygon (m/center-point mbr))
                      ;; Return the first LR that we can find using a test point
                      (first (filter identity
                                     (map (partial even-lr-search polygon)
                                          (shape->lr-search-points polygon)))))]
     (if initial-lr
       ;; Grow LR in all directions
       (->> initial-lr
            ;; Grow corners
            (lr-search [:west :north] polygon)
            (lr-search [:north :east] polygon)
            (lr-search [:east :south] polygon)
            (lr-search [:south :west] polygon)
            ;; Grow edges
            (lr-search [:north] polygon)
            (lr-search [:east] polygon)
            (lr-search [:south] polygon)
            (lr-search [:west] polygon))
       (if not-found-is-error?
         (errors/internal-error! (str "Could not find lr from polygon " (pr-str polygon)))
         (do
           (warn "Use mbr from one of the points in the polygon because lr is not found "
                 (pr-str polygon))
           (m/point->mbr (-> polygon :rings first :points first))))))))

(comment
 (require '[cmr.spatial.kml :as kml])
 (require '[cmr.spatial.circle :as cr])
 (let [center-point (p/point -87.629717 41.878112)
       circle1 (d/calculate-derived (cr/circle center-point 10000))
       polygon1 (cr/circle->polygon circle1 32)
       pm (.mbr (d/calculate-derived polygon1))
       pl (find-lr polygon1)]
   ; (println (kml/shapes->kml [(poly/boundary polygon1)]))
   (kml/display-shapes [pm pl (poly/boundary polygon1)]))
)
