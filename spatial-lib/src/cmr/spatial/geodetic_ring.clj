(ns cmr.spatial.geodetic-ring
  (:require
   [cmr.common.dev.record-pretty-printer :as record-pretty-printer]
   [cmr.common.util :as util]
   [cmr.spatial.arc :as a]
   [cmr.spatial.conversion :as c]
   [cmr.spatial.derived :as d]
   [cmr.spatial.math :refer :all]
   [cmr.spatial.mbr :as mbr]
   [cmr.spatial.point :as p]
   [primitive-math])
  (:import cmr.spatial.arc.Arc))

(primitive-math/use-primitive-operators)

(def ^:const ^:private EXTERNAL_POINT_PRECISION
  "Defines the precision in degrees to use when generating external points to a ring and when
  comparing a point for use with an external point."
  4)

(defrecord GeodeticRing
  [;; The points that make up the ring. Points must be in counterclockwise order. The last point
   ;; must match the first point.
   points
   ;;
   ;; Derived fields
   ;;
   ;; A set of the unique points in the ring.
   ;; This should be used as opposed to creating a set from the points many times over which is expensive.
   point-set
   ;; The arcs of the ring
   arcs
   ;; This attribute contains the rotation direction for the ring. Rotation direction will be one of
   ;; :clockwise, :counter-clockwise, or :none to indicate the point order direction.
   ;; * :clockwise indicates the points are listed in a clockwise order around a center point.
   ;; * :counter-clockwise indicates the points are listed in a counter clockwise order around a center point.
   ;; * :none indicates the point order is around the earth like a belt.
   ;; Depending on the order it could contain the south or north pole.
   course-rotation-direction
   ;; true if ring contains north pole
   contains-north-pole
   ;; true if ring contains south pole
   contains-south-pole
   ;; the minimum bounding rectangle
   mbr
   ;; Three points that are not within the ring. These are used to test if a point is inside or
   ;; outside a ring. We generate multiple external points so that we have a backup if one external
   ;; point is antipodal to a point we're checking is inside a ring.
   external-points])

(record-pretty-printer/enable-record-pretty-printing GeodeticRing)

(defn- arcs-and-arc-intersections
  "Returns the set of intersection points between the arc and the list of arcs. "
  [arcs other-arc]
  (persistent!
    (reduce (fn [s arc]
              (if-let [[point1 point2] (seq (a/intersections arc other-arc))]
                ;; Round the points. If the crossing arc passes through a point on the ring the
                ;; intersection algorithm will result in two very, very close points. By rounding to
                ;; within an acceptable range they'll be seen as the same point.
                (let [s (conj! s (p/round-point p/INTERSECTION_POINT_PRECISION point1))]
                  (if point2
                    (conj! s (p/round-point p/INTERSECTION_POINT_PRECISION point2))
                    s))
                s))
            (transient #{})
            arcs)))

(defn- choose-external-point
  "Finds an external point to use with the ring when testing for coverage of the given point. An
  external point cannot be the same as the point or antipodal to the given point."
  [ring point]
  (let [[ep1 ep2 ep3] (:external-points ring)
        point (p/round-point EXTERNAL_POINT_PRECISION point)
        antipodal-point (p/antipodal point)]
    (cond
      (and (not= ep1 point) (not= ep1 antipodal-point)) ep1
      (and (not= ep2 point) (not= ep2 antipodal-point)) ep2
      (and (not= ep3 point) (not= ep3 antipodal-point)) ep3
      :else
      (throw (Exception. (str "Could not find external point to use to check if ring covers"
                              " point. Ring: " (pr-str ring) " point: " (pr-str point)))))))

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
      (when (mbr/geodetic-covers-point? (:mbr ring) point)
        (if ((:point-set ring) point)
          true ; The point is actually one of the rings points
          ;; otherwise we'll do the real intersection algorithm
          (let [external-point (choose-external-point ring point)
                ;; Create the test arc
                crossing-arc (a/arc point external-point)
                intersections (arcs-and-arc-intersections (:arcs ring) crossing-arc)]
            (or (odd-long? (count intersections))
                ;; if the point itself is one of the intersections then the ring covers it
                (intersections point)))))))

(defn- arcs->course-rotation-direction
  "Calculates the rotation direction of the arcs of a ring. Will be one of :clockwise,
  :counter-clockwise, or :none.

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
  (->GeodeticRing (mapv p/with-geodetic-equality points) nil nil nil nil nil nil nil))

(defn contains-both-poles?
  "Returns true if a ring contains both the north pole and the south pole"
  [ring]
  (and (:contains-north-pole ring)
       (:contains-south-pole ring)))

(defn point-order?
  "Returns true if the points are in counter clockwise order"
  [ring]
  (= (:course-rotation-direction ring) :clockwise))

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
          lon-rotation-direction (->> points (mapv :lon) rotation-direction)

          contains-north-pole (or (some p/is-north-pole? points)
                                  (some a/crosses-north-pole? arcs)
                                  (and (= :none course-rotation-direction)
                                       (= :counter-clockwise lon-rotation-direction)))

          contains-south-pole (or (some p/is-south-pole? points)
                                  (some a/crosses-south-pole? arcs)
                                  (and (= :none course-rotation-direction)
                                       (= :clockwise lon-rotation-direction)))]
      (assoc ring
             :course-rotation-direction course-rotation-direction
             :contains-north-pole contains-north-pole
             :contains-south-pole contains-south-pole))))

(defn ring->point-order
  "Returns the direction of the arcs of a ring, either :clockwise, :counter-clockwise, or :none."
  [^GeodeticRing ring]
  (arcs->course-rotation-direction (ring->arcs ring)))

(defn ring->mbr
  "Determines the mbr from the points in the ring."
  [^GeodeticRing ring]
  (or (.mbr ring)
      (let [arcs (ring->arcs ring)
            {:keys [contains-north-pole contains-south-pole]} (ring->pole-containment ring)
            br (reduce (fn [br ^Arc arc]
                         (let [mbr1 (.mbr1 arc)
                               br (if br
                                    (mbr/union br mbr1)
                                    mbr1)
                               mbr2 (.mbr2 arc)]
                           (if mbr2
                             (mbr/union br mbr2)
                             br)))
                       nil
                       arcs)
            br (if (and contains-north-pole
                        (not-any? p/is-north-pole? (:points ring))
                        (not-any? a/crosses-north-pole? arcs))
                 (mbr/mbr -180.0 90.0 180.0 (:south br))
                 br)]
        (if (and contains-south-pole
                 (not-any? p/is-south-pole? (:points ring))
                 (not-any? a/crosses-south-pole? arcs))
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
      (mapv #(p/round-point EXTERNAL_POINT_PRECISION %) (mbr/external-points br)))))

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


