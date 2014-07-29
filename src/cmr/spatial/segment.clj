(ns cmr.spatial.segment
  "This contains functions for operating on cartesian line segments. These are defined as lines
  that exist in a two dimensional plane."
  (:require [cmr.spatial.math :refer :all]
            [primitive-math]
            [pjstadig.assertions :as pj]
            [cmr.spatial.validation :as v]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.point :as p]
            [cmr.spatial.messages :as msg]
            [cmr.common.services.errors :as errors]
            [cmr.spatial.line :as l]
            [cmr.spatial.derived :as d]
            [cmr.common.util :as util])
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
  [^Point p1 ^Point p2]
  (let [lon1 (.lon p1)
        lat1 (.lat p1)
        lon2 (.lon p2)
        lat2 (.lat p2)
        m (/ (- lat2 lat1) (- lon2 lon1))
        b (- lat1 (* m lon1))
        mbr (m/union (m/point->mbr p1)
                     (m/point->mbr p2)
                     ;; Resulting MBR should not cross the antimeridian as this isn't allowed for cartesian polygons
                     false)]
    (->LineSegment p1 p2 m b mbr)))

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

(defn points->line-segments
  "Takes a list of points and returns arcs connecting all the points"
  [points]
  (util/map-n (partial apply line-segment) 2 1 points))

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
  line will not have derived fields calculated."
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

(defn mbr->line-segments
  "Returns line segments representing the exerior of the MBR"
  [mbr]
  (let [[ul ur lr ll] (m/corner-points mbr)]
    [(line-segment ul ur)
     (line-segment ur lr)
     (line-segment lr ll)
     (line-segment ll ul)]))

(defn- intersection-both-vertical
  "Returns the intersection point of two vertical line segments if they do intersect"
  [^LineSegment ls1 ^LineSegment ls2]
  (let [mbr1 (.mbr ls1)
        mbr2 (.mbr ls2)
        lon1 (get-in ls1 [:point1 :lon])
        lon2 (get-in ls2 [:point1 :lon])
        {ls1-north :north ls1-south :south} mbr1
        {ls2-north :north ls2-south :south} mbr2]
    (when (= lon1 lon2)
      (cond
        (within-range? ls2-north ls1-south ls1-north)
        (p/point lon1 ls2-north)

        (within-range? ls2-south ls1-south ls1-north)
        (p/point lon1 ls2-south)

        (within-range? ls1-south ls2-south ls2-north)
        (p/point lon1 ls1-south)

        :else
        ;; the latitude ranges don't intersect
        nil))))

(defn- intersection-one-vertical
  "Returns the intersection point of one vertical line and another not vertical."
  [ls1 ls2]
  (let [[ls vert-ls] (if (vertical? ls1) [ls2 ls1] [ls1 ls2])
        lon (get-in vert-ls [:point1 :lon])
        mbr (:mbr ls)
        vert-mbr (:mbr vert-ls)]
    (when-let [point (some->> (segment+lon->lat ls lon)
                              (p/point lon))]
      (when (and (m/covers-point? mbr point) (m/covers-point? vert-mbr point))
        point))))

(defn- intersection-normal
  "Returns the intersection of two normal line segments"
  [^LineSegment ls1 ^LineSegment ls2]
  (let [^double m1 (.m ls1)
        ^double b1 (.b ls1)
        ^double m2 (.m ls2)
        ^double b2 (.b ls2)
        mbr1 (.mbr ls1)
        mbr2 (.mbr ls2)
        lon (/ (- b2 b1) (- m1 m2))
        lat (+ (* m1 lon) b1)
        point (p/point lon lat)]
    (when (and (m/covers-point? mbr1 point)
               (m/covers-point? mbr2 point))
      point)))

(defn intersection
  "Returns the intersection point of the line segments if they do intersect."
  [ls1 ls2]
  (let [ls1-vert? (vertical? ls1)
        ls2-vert? (vertical? ls2)]
    (cond
      (and ls1-vert? ls2-vert?)
      (intersection-both-vertical ls1 ls2)

      (or ls1-vert? ls2-vert?)
      (intersection-one-vertical ls1 ls2)

      :else
      (intersection-normal ls1 ls2))))

(defn mbr-intersections
  "Returns the points the line segment intersects the edges of the mbr"
  [ls mbr]
  (let [edges (mbr->line-segments mbr)]
    (filter identity (map (partial intersection ls) edges))))

;; TODO add tests for this
(defn subselect
  "Selects a smaller portion of the line segment using an mbr. Will return nil if the line segment
  is not within the mbr"
  [ls mbr]
  (let [{:keys [point1 point2]} ls
        point1-in-mbr (m/covers-point? mbr point1)
        point2-in-mbr (m/covers-point? mbr point2)
        ;; helper function that removes points that are = to 1 and 2
        ;; If the MBR covers the edges of the line segment then the points will be found as intersections
        not-point1-or-2 #(and (not (approx= point1 % COVERS_TOLERANCE))
                              (not (approx= point2 % COVERS_TOLERANCE)))]
    (if (and point1-in-mbr point2-in-mbr)
      ;; Both points are in the mbr so there's no need to subselect
      ls
      (let [intersection-points (mbr-intersections ls mbr)
            intersection-points (filter not-point1-or-2 intersection-points)]
        (cond
          (> (count intersection-points) 2)
          (errors/internal-error! (str "Found too many intersection points " (pr-str intersection-points)))

          (= (count intersection-points) 0)
          nil ; no intersection at all

          (= (count intersection-points) 2)
          (apply line-segment intersection-points)

          ;; the number of intersection points must be 1 for any of the following conditions
          point2-in-mbr
          (if (= point2 (first intersection-points))
            ;; TODO handle the case where point 2 = the intersection point
            (errors/internal-error! "TODO this case needs to be handled")
            (line-segment point2 (first intersection-points)))

          point1-in-mbr
          (if (= point1 (first intersection-points))
            ;; TODO handle the case where point 1 = the intersection point
            (errors/internal-error! "TODO this case needs to be handled")
            (line-segment point1 (first intersection-points)))

          :else
          ;; could be a corner of the MBR
          ;; TODO handle this case
          (errors/internal-error! "TODO this case needs to be handled"))))))


(extend-protocol d/DerivedCalculator
  cmr.spatial.segment.LineSegment
  (calculate-derived ^LineSegment [^LineSegment a] a))