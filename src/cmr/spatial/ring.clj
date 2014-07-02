(ns cmr.spatial.ring
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

(defrecord Ring
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

(defn intersects-ring?
  "Returns true if the rings intersect each other."
  [r1 r2]
  (or
    ;; Do any of the arcs intersect?
    ;; TODO performance improvement: this should use the multiple arc intersection algorithm to avoid O(N^2) intersections
    (some (fn [[a1 a2]]
            (seq (a/intersections a1 a2)))
          (for [a1 (:arcs r1) a2 (:arcs r2)] [a1 a2]))

    ;; Are any of the points in ring 2 inside ring 1?
    (some #(covers-point? r1 %) (:points r2))

    ;; Are any of the points in ring 1 inside ring 2?
    (some #(covers-point? r2 %) (:points r1))))

(defn br-intersections
  "Returns a lazy sequence of the points where the ring arcs intersect the br"
  [ring br]
  (when (mbr/intersects-br? (:mbr ring) br)
    (let [arcs (:arcs ring)
          {:keys [west north east south]} br]
      (if (= north south)
        ;; A zero height mbr
        (mapcat #(a/lat-segment-intersections % north west east) arcs)

        ;; Create vertical arcs for the sides of the br
        (let [west-arc (a/arc (p/point west south) (p/point west north))
              east-arc (a/arc (p/point east south) (p/point east north))]

          (mapcat #(concat ;; intersections with the west side
                           (a/intersections % west-arc)
                           ;; intersections with the east side
                           (a/intersections % east-arc)
                           ;; intersections with the north side
                           (a/lat-segment-intersections % north west east)
                           ;; intersections with the south side
                           (a/lat-segment-intersections % south west east))
                  arcs))))))

(defn covers-br?
  "Returns true if the ring covers the entire br"
  [ring br]
  (let [corner-points (mbr/corner-points br)]
    (and ;; The rings mbr covers the br
         (mbr/covers-mbr? (:mbr ring) br)
         ;; The ring contains all the corner points of the br.
         (every? (partial covers-point? ring) corner-points)

         ;; The ring arcs does not intersect bounding rectangle except on the points of the ring or br.
         (let [acceptable-points (set (concat (:points ring) corner-points))
               intersections (br-intersections ring br)]
           ;; Are there no intersections ...
           (or (empty? intersections)
               ;; Or is every intersection and acceptable point?
               (every? acceptable-points intersections))))))

(defn covers-ring?
  "Returns true if the ring covers the other ring."
  [ring1 ring2]
  (let [ring1-arcs (:arcs ring1)]
    (and (every? (partial covers-point? ring1) (:points ring2))
         (not-any? (fn [a1]
                     (some (partial a/intersects? a1) ring1-arcs))
                   (:arcs ring2)))))

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
      (let [arcs (:arcs ring)
            {:keys [west north east south]} br]

        (if (= north south)
          ;; A zero height mbr. It's basically a single lat segment.
          (some #(a/intersects-lat-segment? % north west east) arcs)
          (or
            ;; intersections with the north side
            (some #(a/intersects-lat-segment? % north west east) arcs)

            ;; intersections with the south side
            (some #(a/intersects-lat-segment? % south west east) arcs)

            ;; intersections with the west side
            (let [west-arc (a/arc (p/point west south) (p/point west north))]
              (some #(a/intersects? % west-arc) arcs))

            ;; intersections with the east side
            (let [east-arc (a/arc (p/point east south) (p/point east north))]
              (some #(a/intersects? % east-arc) arcs))))))))


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


(comment
  (let [ords [ -78.4111074120776 -41.55810108186105 -78.40288285484534 -41.41987483268612
              -78.591143372866 -41.41345428632509 -78.59968818214728 -41.55167244952579
              -78.4111074120776 -41.55810108186105]
        r1 (ords->ring -55.3,30 -55.3,27, -43,27, -43,30, -55.3,30)]
    (criterium.core/with-progress-reporting
      (criterium.core/bench
        (intersects-ring? r1 (apply ords->ring ords)))))


  (for [a1 (range 3) a2 (range 3)] [a1 (str a2)])

  )

(defn- rotation-direction
  "A helper function that determines the final rotation direction based on a set of angles in
  degrees. It works by summing the differences between each angle. A net negative means clockwise,
  net positive is counter clockwise, and approximatly zero means that there was no net turn in either
  direction.
  Returns one of three keywords, :none, :counter-clockwise, or :clockwise, to indicate net direction
  of rotation"
  [angles]
  (let [angle-delta (fn [^double a1 ^double a2]
                      (let [a2 (if (< a2 a1)
                                 ;; Shift angle 2 so it is always greater than angle 1. This allows
                                 ;; us to get the real radial distance between angle 2 and angle 1
                                 (+ 360.0 a2)
                                 a2)
                            ;; Then when we subtract angle 1 from angle 2 we're asking "How far do
                            ;; we have to turn to the  left to get to angle 2 from angle 1?"
                            left-turn-amount (- a2 a1)]
                        ;; Determine which is smaller: turning to the left or turning to the right
                        (cond
                          ;; In this case we can't determine whether turning to the left or the
                          ;; right is smaller. We handle this by returning 0. Summing the angle
                          ;; deltas in this case will == 180 or -180
                          (== 180.0 left-turn-amount) 0
                          ;; Turning to the right is less than turning to the left in this case.
                          ;; Returns a negative number between 0 and -180.0
                          (> left-turn-amount 180.0) (- left-turn-amount 360.0)
                          :else left-turn-amount)))

        ;; Calculates the amount of change between each angle.
        ;; Positive numbers are turns to the left (counter-clockwise).
        ;; Negative numbers are turns to the right (clockwise)
        deltas (util/map-n (partial apply angle-delta) 2 1 angles)

        ;; Summing the amounts of turn will give us a net turn. If it's positive then there
        ;; is a net turn to the right. If it's negative then there's a net turn to the left.
        ^double net (loop [m 0.0 deltas deltas]
                      (if (empty? deltas)
                        m
                        (recur (+ m ^double (first deltas))
                               (rest deltas))))]
    (cond
      (< (abs net) 0.01) :none
      (> net 0.0) :counter-clockwise
      :else :clockwise)))

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
  (->Ring points nil nil nil nil nil nil nil))

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
  [^Ring ring]
  (or (.arcs ring)
      (a/points->arcs (.points ring))))

(defn ring->pole-containment
  "Returns the ring with north and south pole containment determined"
  [^Ring ring]
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
  [^Ring ring]
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
  [^Ring ring]
  (let [br (ring->mbr ring)
        {:keys [contains-north-pole contains-south-pole]} (ring->pole-containment ring)]
    (if (and contains-north-pole contains-south-pole)
      ;; Cannot determine external points of a ring which contains both north and south poles
      ;; This is an additional feature which could be added at a later time.
      []
      (mbr/external-points br))))

(extend-protocol d/DerivedCalculator
  cmr.spatial.ring.Ring
  (calculate-derived
    [^Ring ring]
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
  cmr.spatial.ring.Ring
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

