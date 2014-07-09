(ns cmr.spatial.segment
  "This contains functions for operating on cartesian line segments. These are defined as lines
  that exist in a two dimensional plane."
  (:require [cmr.spatial.math :refer :all]
            [primitive-math]
            [pjstadig.assertions :as pj]
            [cmr.spatial.derived :as d]
            [cmr.spatial.validation :as v]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.point :as p]
            [cmr.spatial.messages :as msg]
            [cmr.common.services.errors :as errors]
            [cmr.spatial.line :as l])
  (:import cmr.spatial.point.Point
           cmr.spatial.mbr.Mbr))

(def ^:const COVERS_TOLERANCE
  "Tolerance used for the determining if points are on the line."
  0.00000001)

(primitive-math/use-primitive-operators)

(defrecord LineSegment
  [
   ^Point point1
   ^Point point2

   ;; Derived Fields
   ;; Fields from the formula for a line: y = m*x + b

   ;; The slope of a line. A vertical line will not have meaningful slope or slope-intercept
   ^Double m

   ;; The slope intercept
   ^Double b

   ;; The minimum bounding rectangle of the segment
   ^Mbr mbr
   ])

(defn line-segment
  "Creates a new line segment"
  [p1 p2]
  (->LineSegment p1 p2 nil nil nil))

(defn ords->line-segment
  "Takes all arguments as coordinates for points, lon1, lat1, lon2, lat2, and creates an line-segment."
  [& ords]
  (apply line-segment (apply p/ords->points ords)))

(defn line-segment->ords
  "Returns a list of the line-segment ordinates lon1, lat1, lon2, lat2"
  [ls]
  (let [{{lon1 :lon lat1 :lat} :point1
         {lon2 :lon lat2 :lat} :point2} ls]
    [lon1, lat1, lon2, lat2]))

(defn vertical?
  "Returns true if this is a vertical line segment"
  [^LineSegment ls]
  (let [^Point p1 (.point1 ls)
        ^Point p2 (.point2 ls)]
    (= (.lon p1) (.lon p2))))

(defn horizontal?
  "Returns true if this is a horizontal line segment"
  [^LineSegment ls]
  (let [^Point p1 (.point1 ls)
        ^Point p2 (.point2 ls)]
    (= (.lat p1) (.lat p2))))

(defn segment+lon->lat
  "Returns the latitude of the line at the given longitude. Fails with runtime error for vertical lines."
  [ls ^double lon]
  (when (vertical? ls)
    (errors/internal-error! "Can not determine latitude of points at a given longitude in a vertical line"))

  (let [{:keys [^double m ^double b mbr]} ls]
    (when (m/covers-lon? mbr lon)
      (+ (* m lon) b))))

(defn segment+lat->lon
  "Returns the longitude of the line at the given latitude. Returns nil if outside the bounds of the
  line segment. Fails with runtime error for horizontal lines because the longitude at the latitude
  of the line would be every longitude"
  ^double [ls ^double lat]
  (when (horizontal? ls)
    (errors/internal-error! "Can not determine longitude of points at a given latitude in a horizontal line"))
  (if (vertical? ls)
    (-> ls :point1 :lon)
    (let [{:keys [^double m ^double b mbr]} ls]
      (when (m/covers-lat? mbr lat)
        (/ (- lat b) m)))))

(defn point-on-segment?
  "Returns true if the point is approximately on the segment."
  [ls point]
  (let [mbr (:mbr ls)]
    (when (m/covers-point? mbr point)
      (if (horizontal? ls)
        (approx= ^double (get-in ls [:point1 :lat])
                 ^double (:lat point) COVERS_TOLERANCE)
        (when-let [expected-lon (segment+lat->lon ls (:lat point))]
          (approx= expected-lon ^double (:lon point) COVERS_TOLERANCE))))))

(defn distance
  "Calculates the distance of the line segment using the equation for a right triangle's hypotenuse."
  (^double [^LineSegment ls]
   (distance (.point1 ls) (.point2 ls)))
  (^double [^Point point1 ^Point point2]
   (let [lon1 (.lon point1)
         lat1 (.lat point1)
         lon2 (.lon point2)
         lat2 (.lat point2)
         a (- lat2 lat1)
         b (- lon2 lon1)]
     (sqrt (+ (sq a) (sq b))))))

(defn line-segment->line
  "Creates an approximate geodetic line of the line segment by desifying the line segment. Optionally
  accepts densification distance in degrees. Does no densification for vertical lines. The returned
  line will not have derived fields calculated.

  TODO if we have a horizontal line we probably want to use a different approach in the caller.
  "
  ([ls]
   (line-segment->line ls 0.1))
  ([^LineSegment ls ^double densification-dist]
   (if (vertical? ls)
     (l/line [(:point1 ls) (:point2 ls)])
     (let [^Point p1 (.point1 ls)
           ^Point p2 (.point2 ls)
           lon1 (.lon p1)
           lat1 (.lat p1)
           lon2 (.lon p2)
           lat2 (.lat p2)
           m (.m ls)
           ;; convert slope to angle
           angle-a (atan m)
           ;; Calculate the difference to add for each point to the latitude and the longitude
           ;; sin A = a/c
           ;; c is the hypotenuse of the right triangle. In this case the distance of
           ;; c is the amount to densify.
           lat-diff  (* densification-dist (sin angle-a))
           ;; cos A = b/c
           lon-diff  (* densification-dist (cos angle-a))

           ;; If the line is going backwards flip the signs
           [^double lat-diff
            ^double lon-diff] (if (> lon1 lon2)
                                [(* -1.0 lat-diff) (* -1.0 lon-diff)]
                                [lat-diff lon-diff])
           num-points (int (Math/floor (/ (distance ls)
                                          densification-dist)))
           initial-lon lon1
           initial-lat lat1
           points (mapv #(p/point (+ initial-lon (* lon-diff (double %)))
                                  (+ initial-lat (* lat-diff (double %))))
                        (range (inc num-points)))
           points (if (not= (last points) p2)
                    (conj points p2)
                    points)]
       (l/line points)))))


(comment

(line-segment->line (d/calculate-derived (ords->line-segment 0 0 1 0)) 2.0)

)


(defn slope
  "Returns or calculates the slope of a line segment."
  ^double [^LineSegment ls]
  (or (:m ls)
      (let [^Point p1 (.point1 ls)
            ^Point p2 (.point2 ls)
            lon1 (.lon p1)
            lat1 (.lat p1)
            lon2 (.lon p2)
            lat2 (.lat p2)]
        (/ (- lat2 lat1) (- lon2 lon1)))))

(defn slope-intersect
  "Returns or calculates the slope intersect of a line segment."
  ^double [^LineSegment ls]
  (or (:b ls)
      (let [^Point p1 (.point1 ls)
            lon1 (.lon p1)
            lat1 (.lat p1)
            m (slope ls)]
        (- lat1 (* m lon1)))))

(defn mbr
  "Returns or calculates the minimumber bounding rectangle of a line segment"
  [ls]
  (or (:mbr ls)
      (m/union (m/point->mbr (:point1 ls))
               (m/point->mbr (:point2 ls))
               ;; Resulting MBR should not cross the antimeridian as this isn't allowed for cartesian polygons
               false)))

(extend-protocol d/DerivedCalculator
  cmr.spatial.segment.LineSegment
  (calculate-derived
    ^LineSegment [^LineSegment ls]
    (-> ls
        (assoc :m (slope ls))
        (assoc :b (slope-intersect ls))
        (assoc :mbr (mbr ls)))))

