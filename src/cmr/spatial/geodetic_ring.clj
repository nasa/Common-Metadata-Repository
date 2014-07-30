(ns cmr.spatial.geodetic-ring
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.math :refer :all]
            [cmr.common.util :as util]
            [primitive-math]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.conversion :as c]
            [cmr.spatial.arc :as a]
            [cmr.spatial.derived :as d]
            [clojure.math.combinatorics :as combo]
            [cmr.spatial.validation :as v]
            [cmr.spatial.messages :as msg])
  (:import cmr.spatial.arc.Arc))
(primitive-math/use-primitive-operators)

(defrecord GeodeticRing
  [
   ;; The points that make up the ring. Points must be in counterclockwise order. The last point
   ;; must match the first point.
   points

   ;; Derived fields

   ;; A set of the unique points in the ring.
   ;; This should be used as opposed to creating a set from the points many times over which is expensive.
   point-set

   ;; The arcs of the ring
   arcs

   ;; This attribute contains the rotation direction for the ring. Rotation direction will be one of
   ;; :clockwise, :counter_clockwise, or :none to indicate the point order direction.
   ;; * :clockwise indicates the points are listed in a clockwise order around a center point.
   ;; * :counter_clockwise indicates the points are listed in a counter clockwise order around a center point.
   ;; * :none indicates the point order is around the earth like a belt.
   ;; Depending on the order it could contain the south or north pole.
   course-rotation-direction

   ;; true if ring contains north pole
   contains-north-pole

   ;; true if ring contains south pole
   contains-south-pole

   ;; the minimum bounding rectangle
   mbr

   ;; Two points that are not within the ring. These are used to test if a point is inside or
   ;; outside a ring. We generate multiple external points so that we have a backup if one external
   ;; point is antipodal to a point we're checking is inside a ring.
   external-points
   ])

(defn covers-point?
  "Determines if a ring covers the given point. The algorithm works by counting the number of times
  an arc between the point and a known external point crosses the ring. An even count means the point
  is external. An odd count means the point is inside the ring."
  [ring point]
  ;; The pre check is necessary for rings which might contain both north and south poles
  {:pre [(> (count (:external-points ring)) 0)]}

  (or (and (:contains-north-pole ring) (p/is-north-pole? point))
      (and (:contains-south-pole ring) (p/is-south-pole? point))
      ;; Only do real intersection if the mbr covers the point.
      (when (mbr/covers-point? (:mbr ring) point)
        (if (some (:point-set ring) point)
          true ; The point is actually one of the rings points
          ;; otherwise we'll do the real intersection algorithm
          (let [antipodal-point (p/antipodal point)
                ;; Find an external point to use. We can't use an external point that is antipodal
                ;; to the given point or equal to the point.
                external-point (first (filter #(and (not= % antipodal-point)
                                                    (not= % point))
                                              (:external-points ring)))
                ;; Create the test arc
                crossing-arc (a/arc point external-point)
                ;; Find all the points the arc passes through
                intersections (mapcat #(a/intersections % crossing-arc) (:arcs ring))
                ;; Round the points. If the crossing arc passes through a point on the ring the
                ;; intersection algorithm will result in two very, very close points. By rounding to
                ;; within an acceptable range they'll be seen as the same point.
                intersections (set (map (partial p/round-point 5) intersections))]
            (or (odd? (count intersections))
                ;; if the point itself is one of the intersections then the ring covers it
                (intersections point)))))))

(defn self-intersections
  "Returns the rings self intersections"
  [ring]
  (let [arcs (:arcs ring)
        ;; Finds the indexes of the arcs in the list to test intersecting together.
        ;; Works by finding all combinations and rejecting the arcs would be sequential.
        ;; (The first and second arc naturally touch on a shared point for instance.)
        arc-test-indices (filter (fn [[^int n1 ^int n2]]
                                   (not (or ; Reject sequential indexes
                                            (= n1 (dec n2))
                                            ;; Reject the last arc combined with first arc.
                                            (and
                                              (= n1 0)
                                              (= n2 (dec (count arcs)))))))
                                 (combo/combinations (range (count arcs)) 2))]
    (mapcat (fn [[n1 n2]]
              (let [a1 (nth arcs n1)
                    a2 (nth arcs n2)]
                (a/intersections a1 a2)))
            arc-test-indices)))


(defn- arcs->course-rotation-direction
  "Calculates the rotation direction of the arcs of a ring. Will be one of :clockwise,
  :counter_clockwise, or :none.

  It works by calculating the number of degrees of turning that the ring does. It gets the initial
  and ending course from each arc. It determines how many degrees each turn is. Turns to the left,
  counter clockwise, are positive. Turns to the right, clockwise, are negative. This adds all of the
  differences together to get the net bearing change while traveling around the ring. A normal
  counter clockwise ring will be approximately 360 degrees of turn. A clockwise ring will be -360.
  A ring around a pole will be approximately 0 net degrees turn. If a ring crosses or has a point on
  a single pole then the sum will be -180 or 180. If a ring crosses both poles then the sum will be
  0."
  [arcs]
  ;; Gets a list of the arc initial and ending courses to show all the angles that are travelled on
  ;; throughout the ring.
  (let [courses (loop [courses (transient []) arcs arcs]
                  (if (empty? arcs)
                    (persistent! courses)
                    (let [^Arc a (first arcs)]
                      (recur (-> courses
                                 (conj! (.initial_course a))
                                 (conj! (.ending_course a)))
                             (rest arcs)))))
        ;; Add the first turn angle on again to complete the turn
        courses (conj courses (first courses))]
    (rotation-direction courses)))

(defn ring
  "Creates a new ring with the given points. If the other fields of a ring are needed. The
  calculate-derived function should be used to populate it."
  [points]
  (->GeodeticRing points nil nil nil nil nil nil nil))

(defn contains-both-poles?
  "Returns true if a ring contains both the north pole and the south pole"
  [ring]
  (and (:contains-north-pole ring)
       (:contains-south-pole ring)))

(defn invert
  "Returns the inverse of the ring. It will cover the exact opposite area on the earth."
  [r]
  (ring (reverse (:points r))))

(defn ring->arcs
  "Determines the arcs from the points in the ring."
  [^GeodeticRing ring]
  (or (.arcs ring)
      (a/points->arcs (.points ring))))

(defn ring->pole-containment
  "Returns the ring with north and south pole containment determined"
  [^GeodeticRing ring]
  (if (:course-rotation-direction ring)
    ring
    (let [arcs (ring->arcs ring)
          points (.points ring)
          course-rotation-direction (arcs->course-rotation-direction arcs)
          ;; The net rotation direction of the longitudes of the ring around the earth if looking
          ;; down on the north pole
          lon-rotation-direction (->> points (map :lon) rotation-direction)

          contains-north-pole (or (some p/is-north-pole? points)
                                  (some a/crosses-north-pole? arcs)
                                  (= :clockwise course-rotation-direction)
                                  (and (= :none course-rotation-direction)
                                       (= :counter-clockwise lon-rotation-direction)))

          contains-south-pole (or (some p/is-south-pole? points)
                                  (some a/crosses-south-pole? arcs)
                                  (= :clockwise course-rotation-direction)
                                  (and (= :none course-rotation-direction)
                                       (= :clockwise lon-rotation-direction)))]
      (assoc ring
             :course-rotation-direction course-rotation-direction
             :contains-north-pole contains-north-pole
             :contains-south-pole contains-south-pole))))

(defn ring->mbr
  "Determines the mbr from the points in the ring."
  [^GeodeticRing ring]
  (or (.mbr ring)
      (let [arcs (ring->arcs ring)
            {:keys [contains-north-pole contains-south-pole]} (ring->pole-containment ring)
            br (->> arcs (mapcat a/mbrs) (reduce mbr/union))
            br (if (and contains-north-pole
                        (not (some p/is-north-pole? (:points ring)))
                        (not (some a/crosses-north-pole? arcs)))
                 (mbr/mbr -180.0 90.0 180.0 (:south br))
                 br)]
        (if (and contains-south-pole
                 (not (some p/is-south-pole? (:points ring)))
                 (not (some a/crosses-south-pole? arcs)))
          (mbr/mbr -180.0 (:north br) 180.0 -90.0)
          br))))


(defn ring->external-points
  "Determines external points that are not in the ring."
  [^GeodeticRing ring]
  (let [br (ring->mbr ring)
        {:keys [contains-north-pole contains-south-pole]} (ring->pole-containment ring)]
    (if (and contains-north-pole contains-south-pole)
      ;; Cannot determine external points of a ring which contains both north and south poles
      ;; This is an additional feature which could be added at a later time.
      []
      (mbr/external-points br))))

(extend-protocol d/DerivedCalculator
  cmr.spatial.geodetic_ring.GeodeticRing
  (calculate-derived
    [^GeodeticRing ring]
    (if (.arcs ring)
      ring

      (as-> ring ring
            (assoc ring :point-set (set (:points ring)))
            (assoc ring :arcs (ring->arcs ring))
            (ring->pole-containment ring)
            (assoc ring :mbr (ring->mbr ring))
            (assoc ring :external-points (ring->external-points ring))))))


(defn ords->ring
  "Takes all arguments as coordinates for points, lon1, lat1, lon2, lat2, and creates a ring."
  [& ords]
  (ring (apply p/ords->points ords)))

(defn ring->ords [ring]
  (p/points->ords (:points ring)))

(defn- ring-points-validation
  "Validates the individual points of the ring."
  [{:keys [points]}]
  (mapcat (fn [[i point]]
            (when-let [errors (v/validate point)]
              (map (partial msg/ring-point-invalid i) errors)))
          (map-indexed vector points)))

(defn- ring-closed-validation
  "Validates the ring is closed (last point = first point)"
  [{:keys [points]}]
  (when-not (= (first points) (last points))
    [(msg/ring-not-closed)]))

(defn- points->rounded-point-map
  "Combines together points that round to the same value. Takes a sequence of points and returns a
  map of rounded points to list of index, point pairs."
  [points]
  (reduce (fn [m [i point]]
            (let [rounded (p/round-point 8 point)]
              (update-in m [rounded] conj [i point])))
          {}
          (map-indexed vector points)))

(defn- ring-duplicate-point-validation
  "Validates that the ring does not contain any duplicate or very close together points."
  [{:keys [points]}]

  ;; Create a map of the rounded points to list of points that round that same value. If any of the
  ;; rounded points has more than other point in the list then they are duplicates.
  (let [rounded-point-map (points->rounded-point-map (drop-last points))
        duplicate-point-lists (->> rounded-point-map
                                   vals
                                   (filter #(> (count %) 1))
                                   ;; reversing lists of duplicate points to put points in indexed order
                                   ;; for more pleasing messages.
                                   (map reverse))]
    (map msg/ring-duplicate-points duplicate-point-lists)))

(defn- ring-consecutive-antipodal-points-validation
  "Validates that the ring does not have any consecutive antipodal points"
  [{:keys [points]}]

  (let [indexed-points (map-indexed vector points)
        indexed-point-pairs (partition 2 1 indexed-points)
        antipodal-indexed-point-pairs (filter (fn [[[_ p1] [_ p2]]]
                                                (p/antipodal? p1 p2))
                                              indexed-point-pairs)]
    (map (partial apply msg/ring-consecutive-antipodal-points)
         antipodal-indexed-point-pairs)))

(defn- ring-self-intersection-validation
  "Validates that the ring does not intersect itself"
  [ring]
  (when-let [intersections (seq (self-intersections ring))]
    [(msg/ring-self-intersections intersections)]))

(defn- ring-pole-validation
  "Validates that the ring does not contain both poles"
  [ring]
  (let [ring (ring->pole-containment ring)]
    (when (and (:contains-south-pole ring) (:contains-north-pole ring))
      [(msg/ring-contains-both-poles)])))

(extend-protocol v/SpatialValidation
  cmr.spatial.geodetic_ring.GeodeticRing
  (validate
    [ring]
    ;; Certain validations can only be run if earlier validations passed. Validations are grouped
    ;; here so that subsequent validations won't run if earlier validations fail.

    (or (seq (ring-points-validation ring))
        ;; basic ring validation
        (or (seq (concat (ring-closed-validation ring)
                         (ring-duplicate-point-validation ring)
                         (ring-consecutive-antipodal-points-validation ring)))
            ;; Advanced ring validation
            (let [ring (assoc ring :arcs (ring->arcs ring))]
              (or (seq (ring-self-intersection-validation ring))
                  (seq (ring-pole-validation ring))))))))

