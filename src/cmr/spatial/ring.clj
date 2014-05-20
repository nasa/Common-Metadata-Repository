(ns cmr.spatial.ring
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.math :refer :all]
            [cmr.common.util :as util]
            [primitive-math]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.conversion :as c]
            [cmr.spatial.arc :as a]
            [cmr.spatial.derived :as d])
  (:import cmr.spatial.arc.Arc))
(primitive-math/use-primitive-operators)

(defrecord Ring
  [
   ;; The points that make up the ring. Points must be in counterclockwise order. The last point
   ;; must match the first point.
   points

   ;; Derived fields

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
  ;; Only do real intersection if the ring covers the point.
  (when (mbr/covers-point? (:mbr ring) point)
    (if (some (set (:points ring)) point)
      true ; The point is actually one of the rings points
      ;; otherwise we'll do the real intersection algorithm
      (let [antipodal-point (p/antipodal point)
            ;; Find an external point to use. We can't use an external point that is antipodal to the given point or equal to the point.
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
            (intersections point))))))

(defn intersects-ring?
  "Returns true if the rings intersect each other."
  [r1 r2]
  (or
    ;; Do any of the arcs intersect?
    ;; TODO this should use the multiple arc intersection algorithm to avoid O(N^2) intersections
    (some (fn [[a1 a2]]
               (seq (a/intersections a1 a2)))
          (for [a1 (:arcs r1) a2 (:arcs r2)] [a1 a2]))

    ;; Are any of the points in ring 2 inside ring 1?
    (some #(covers-point? r1 %) (:points r2))

    ;; Are any of the points in ring 1 inside ring 2?
    (some #(covers-point? r2 %) (:points r1))))


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
        deltas (util/map-n angle-delta 2 1 angles)

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
  (->Ring points nil nil nil nil nil nil))

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
            br (if contains-north-pole
                 (mbr/mbr -180.0 90.0 180.0 (:south br))
                 br)]
        (if contains-south-pole
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

