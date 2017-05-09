(ns cmr.spatial.line-segment
  "This contains functions for operating on cartesian line segments. These are defined as lines
  that exist in a two dimensional plane."
  (:require
   [clojure.math.combinatorics :as combo]
   [cmr.common.dev.record-pretty-printer :as record-pretty-printer]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.spatial.derived :as d]
   [cmr.spatial.math :refer :all]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.messages :as msg]
   [cmr.spatial.point :as p]
   [pjstadig.assertions :as pj]
   [primitive-math])
  (:import cmr.spatial.point.Point
           cmr.spatial.mbr.Mbr))

(def ^:const COVERS_TOLERANCE
  "Tolerance used for the determining if points are on the line."
 0.00001)

(primitive-math/use-primitive-operators)

(defrecord LineSegment
  [^Point point1
   ^Point point2
   ;; Derived Fields
   ;; true if the line is vertical
   vertical
   ;; true if the line is horizontal
   horizontal
   ;;
   ;; Fields from the formula for a line: y = m*x + b
   ;;
   ;; The slope of a line. A vertical line will not have meaningful slope or slope-intercept
   ^Double m
   ;; The slope intercept
   ^Double b
   ;; The minimum bounding rectangle of the segment
   ^Mbr mbr])

(record-pretty-printer/enable-record-pretty-printing LineSegment)

(defn- points->mbr
  "Returns an MBR that covers both points but does not cross the antimeridian"
  [^double lon1 ^double lat1 ^double lon2 ^double lat2]
  (let [n (max lat1 lat2)
        s (min lat1 lat2)]
    (if (> lon2 lon1)
      (m/mbr lon1 n lon2 s)
      (m/mbr lon2 n lon1 s))))

(defn line-segment
  "Creates a new line segment"
  [^Point p1 ^Point p2]
  (let [lon1 (.lon p1)
        lat1 (.lat p1)
        lon2 (.lon p2)
        lat2 (.lat p2)
        vertical? (= lon1 lon2)
        horizontal? (= lat1 lat2)
        ^double m (cond
                    vertical? infinity
                    horizontal? 0.0
                    :else (/ (- lat2 lat1) (- lon2 lon1)))
        b (- lat1 (* m lon1))
        ;; Resulting MBR should not cross the antimeridian as this isn't allowed for cartesian polygons
        mbr (points->mbr lon1 lat1 lon2 lat2)]
    (->LineSegment (p/with-cartesian-equality p1)
                   (p/with-cartesian-equality p2)
                   vertical?
                   horizontal?
                   m b mbr)))

(defn ords->line-segment
  "Takes all arguments as coordinates for points, lon1, lat1, lon2, lat2, and creates an line-segment."
  [& ords]
  (apply line-segment (p/ords->points ords)))

(defn line-segment->ords
  "Returns a list of the line-segment ordinates lon1, lat1, lon2, lat2"
  [ls]
  (let [{{lon1 :lon lat1 :lat} :point1
         {lon2 :lon lat2 :lat} :point2} ls]
    [lon1, lat1, lon2, lat2]))

(defn points->line-segments
  "Takes a list of points and returns arcs connecting all the points"
  [points]
  (let [points (vec points)]
    (persistent!
     (reduce (fn [lines ^long index]
               (conj! lines (line-segment (nth points index) (nth points (inc index)))))
             (transient [])
             (range 0 (dec (count points)))))))

(defn vertical?
  "Returns true if this is a vertical line segment"
  [^LineSegment ls]
  (.vertical ls))

(defn horizontal?
  "Returns true if this is a horizontal line segment"
  [^LineSegment ls]
  (.horizontal ls))

(defn course
  "Returns the compass heading along the line"
  [ls]
  (let [{:keys [point1 point2 m]} ls
        slope-angle (degrees (atan m))
        {^double lat1 :lat ^double lon1 :lon} point1
        {^double lat2 :lat ^double lon2 :lon} point2]
    (cond
      (= lon1 lon2)
      ; a vertical line
      (if (> lat1 lat2)
        180.0
        360.0)

      (> lon1 lon2)
      (+ 90.0 slope-angle)

      :else ; lon1 < lon2
      (+ 270.0 slope-angle))))

(defn segment+lon->lat
  "Returns the latitude of the line at the given longitude. Fails with runtime error for vertical lines."
  [^LineSegment ls ^double lon]
  (when (.vertical ls)
    (errors/internal-error! "Can not determine latitude of points at a given longitude in a vertical line"))

  (let [^double m (.m ls)
        ^double b (.b ls)
        ^Mbr mbr (.mbr ls)]
    (when (m/cartesian-lon-range-covers-lon? (.west mbr) (.east mbr) lon m/COVERS_TOLERANCE)
      (+ (* m lon) b))))

(defn segment+lat->lon
  "Returns the longitude of the line at the given latitude. Returns nil if outside the bounds of the
  line segment. Fails with runtime error for horizontal lines because the longitude at the latitude
  of the line would be every longitude"
  ^double [^LineSegment ls ^double lat]
  (when (.horizontal ls)
    (errors/internal-error! "Can not determine longitude of points at a given latitude in a horizontal line"))
  (if (.vertical ls)
    (.lon ^Point (.point1 ls))
    (let [^double m (.m ls)
          ^double b (.b ls)
          mbr (.mbr ls)]
      (when (m/covers-lat? mbr lat)
        (/ (- lat b) m)))))

(defn point-on-segment?
  "Returns true if the point is approximately on the segment."
  [^LineSegment ls ^Point point]
  (or (= (.point1 ls) point)
      (= (.point2 ls) point)
      (let [mbr (.mbr ls)
            ^Point point1 (.point1 ls)]
        (when (m/cartesian-covers-point? mbr point)
          (if (.horizontal ls)
            (approx= ^double (.lat point1)
                     ^double (.lat point) COVERS_TOLERANCE)
            (when-let [expected-lon (segment+lat->lon ls (.lat point))]
              (approx= expected-lon ^double (.lon point) COVERS_TOLERANCE)))))))

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

(defn densify-line-segment
  "Returns points along the line segment for approximating the segment in another coordinate system.
  Optionally accepts densification distance in degrees. Does no densification for vertical lines."
  ([ls]
   (densify-line-segment ls 0.1))
  ([^LineSegment ls ^double densification-dist]
   (if (.vertical ls)
     [(:point1 ls) (:point2 ls)]
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
                                  (+ initial-lat (* lat-diff (double %)))
                                  false)
                        (range (inc num-points)))]
       (if (not= (last points) p2)
         (conj points p2)
         points)))))

(defn mbr->line-segments
  "Returns line segments representing the exerior of the MBR. The MBR must cover more than a single point"
  [^Mbr mbr]
  (let [west (.west mbr)
        east (.east mbr)
        north (.north mbr)
        south (.south mbr)]
    (cond
      (m/single-point? mbr)
      (errors/internal-error! "This function doesn't work for an MBR that's a single point.")

      (= west east)
      ;; zero width mbr
      [(line-segment (p/point west north false) (p/point east south false))]

      (= north south)
      ;; zero height mbr
      (if (m/crosses-antimeridian? mbr)
        [(line-segment (p/point west north false) (p/point 180.0 north false))
         (line-segment (p/point -180.0 north false) (p/point east north false))]
        [(line-segment (p/point west north false) (p/point east south false))])

      (m/crosses-antimeridian? mbr)
      (let [[ul ur lr ll] (m/corner-points mbr)
            {:keys [north south]} mbr]
        [(line-segment ul (p/point 180.0 north false))
         (line-segment (p/point -180.0 north false) ur)
         (line-segment ur lr)
         (line-segment lr (p/point -180.0 south false))
         (line-segment (p/point 180.0 south false) ll)
         (line-segment ll ul)])

      :else
      (let [ul (p/point west north false)
            ur (p/point east north false)
            lr (p/point east south false)
            ll (p/point west south false)]
        [(line-segment ul ur)
         (line-segment ur lr)
         (line-segment lr ll)
         (line-segment ll ul)]))))

(defn- intersection-both-vertical
  "Returns the intersection point of two vertical line segments if they do intersect"
  [^LineSegment ls1 ^LineSegment ls2]
  (let [^Mbr mbr1 (.mbr ls1)
        ^Mbr mbr2 (.mbr ls2)
        lon1 (.lon ^Point (.point1 ls1))
        lon2 (.lon ^Point (.point1 ls2))
        ls1-north (.north mbr1)
        ls1-south (.south mbr1)
        ls2-north (.north mbr2)
        ls2-south (.south mbr2)]
    (when (= lon1 lon2)
      (cond
        (within-range? ls2-north ls1-south ls1-north)
        (p/point lon1 ls2-north false)

        (within-range? ls2-south ls1-south ls1-north)
        (p/point lon1 ls2-south false)

        (within-range? ls1-south ls2-south ls2-north)
        (p/point lon1 ls1-south false)

        :else
        ;; the latitude ranges don't intersect
        nil))))

(defn- intersection-one-vertical
  "Returns the intersection point of one vertical line and another not vertical."
  [^LineSegment vert-ls ^LineSegment ls]
  (let [lon (.lon ^Point (.point1 vert-ls))
        mbr (.mbr ls)
        vert-mbr (.mbr vert-ls)]
    (when-let [point (when-let [lat (segment+lon->lat ls lon)]
                       (p/point lon lat false))]
      (when (and (m/cartesian-covers-point? mbr point) (m/cartesian-covers-point? vert-mbr point))
        point))))

(defn- intersection-both-horizontal
  "Returns the intersection point of two horizontal line segments if they do intersect"
  [^LineSegment ls1 ^LineSegment ls2]
  (let [^Mbr mbr1 (.mbr ls1)
        ^Mbr mbr2 (.mbr ls2)
        lat1 (.lat ^Point (.point1 ls1))
        lat2 (.lat ^Point (.point1 ls2))
        ls1-east (.east mbr1)
        ls1-west (.west mbr1)
        ls2-east (.east mbr2)
        ls2-west (.west mbr2)]
    (when (= lat1 lat2)
      (cond
        (within-range? ls2-east ls1-west ls1-east)
        (p/point ls2-east lat1 false)

        (within-range? ls2-west ls1-west ls1-east)
        (p/point ls2-west lat1 false)

        (within-range? ls1-west ls2-west ls2-east)
        (p/point ls1-west lat1 false)

        :else
        ;; the longitude ranges don't intersect
        nil))))

(defn intersection-horizontal-and-vertical
  "Returns the intersection of one horizontal line and one vertical line"
  [^LineSegment horiz-ls ^LineSegment vert-ls]
  (let [^Mbr horiz-mbr (.mbr horiz-ls)
        horiz-lat (.north horiz-mbr)
        horiz-west (.west horiz-mbr)
        horiz-east (.east horiz-mbr)
        ^Mbr vert-mbr (.mbr vert-ls)
        vert-lon (.west vert-mbr)
        vert-south (.south vert-mbr)
        vert-north (.north vert-mbr)]
    (when (and (within-range? horiz-lat vert-south vert-north)
               (within-range? vert-lon horiz-west horiz-east))
      (p/point vert-lon horiz-lat false))))

(defn- intersection-parallel
  "Returns the intersection of two normal line segments that are parallel to each other"
  [^LineSegment ls1 ^LineSegment ls2]
  ;; They will only intersect if slope intercepts are the same.
  (when (= (.b ls1) (.b ls2))
    ;; Find the common intersecting mbr to find a common longitude.
    (when-let [intersection-mbr (first (m/intersections (.mbr ls1) (.mbr ls2)))]
      ;; Use the longitude to find a point
      (p/point (:west intersection-mbr) (segment+lon->lat ls1 (:west intersection-mbr)) false))))

(def ^:const INTERSECTION_COVERS_TOLERANCE
  "Tolerance used for the covers method during point intersections. Longitudes and latitudes
  technically outside the bounding rectangle but within this tolerance will be considered covered by
  the bounding rectangle"
  0.0000001)

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
        point (p/point lon lat false)]
    (when (and (m/cartesian-covers-point? mbr1 point INTERSECTION_COVERS_TOLERANCE)
               (m/cartesian-covers-point? mbr2 point INTERSECTION_COVERS_TOLERANCE))
      point)))

(defn intersection
  "Returns the intersection point of the line segments if they do intersect."
  [^LineSegment ls1 ^LineSegment ls2]
  (let [ls1-vert? (.vertical ls1)
        ls2-vert? (.vertical ls2)
        ls1-horz? (.horizontal ls1)
        ls2-horz? (.horizontal ls2)]

    (cond
      ls1-vert?
      (cond
        ls2-vert? (intersection-both-vertical ls1 ls2)
        ls2-horz? (intersection-horizontal-and-vertical ls2 ls1)
        :else (intersection-one-vertical ls1 ls2))

      ls2-vert?
      (if ls1-horz?
        (intersection-horizontal-and-vertical ls1 ls2)
        (intersection-one-vertical ls2 ls1))

      (and ls1-horz? ls2-horz?)
      (intersection-both-horizontal ls1 ls2)

      (= (.m ls1) (.m ls2))
      (intersection-parallel ls1 ls2)

      :else
      (intersection-normal ls1 ls2))))

(defn mbr-intersections
  "Returns the points the line segment intersects the edges of the mbr"
  [ls mbr]
  (if (m/single-point? mbr)
    (let [point (p/point (:west mbr) (:north mbr) false)]
      (when (point-on-segment? ls point)
        [point]))
    (let [edges (mbr->line-segments mbr)]
      (map p/with-cartesian-equality (filter identity (map (partial intersection ls) edges))))))

(defn keep-farthest-points
  "Takes a list of points and returns the two points that are farthest from each other."
  [points]
  (->> (combo/combinations points 2)
       (map (fn [[p1 p2]]
              {:distance (distance p1 p2)
               :points [p1 p2]}))
       (sort-by :distance)
       last
       :points))

(defn subselect-not-across-am
  "Helper for implementing subselect. Works on an mbr that does not cross the antimeridian"
  [ls mbr]
  (let [{:keys [point1 point2]} ls
        point1-in-mbr (m/cartesian-covers-point? mbr point1)
        point2-in-mbr (m/cartesian-covers-point? mbr point2)]
    (if (and point1-in-mbr point2-in-mbr)
      ;; Both points are in the mbr so there's no need to subselect
      {:line-segments [ls]}
      (let [intersection-points (mbr-intersections ls mbr)
            intersection-points (distinct (map (partial p/round-point 11)
                                               intersection-points))
            ;; There are some cases where the above can generate more than 3 points. This happens
            ;; for things like very close to horizontal lines and a very short mbr.
            intersection-points (if (> (count intersection-points) 2)
                                  ;; Keep the points that are the farthest away from each other
                                  ;; in this case.
                                  (keep-farthest-points intersection-points)
                                  intersection-points)]
        (cond
          (> (count intersection-points) 2)
          (errors/internal-error! (str "Found too many intersection points " (pr-str intersection-points)))

          (zero? (count intersection-points))
          nil ; no intersection at all

          (= (count intersection-points) 2)
          {:line-segments [(apply line-segment intersection-points)]}

          ;; the number of intersection points must be 1 for any of the following conditions
          point2-in-mbr
          (if (approx= point2 (first intersection-points) COVERS_TOLERANCE)
            {:points [point2]} ; Point 2 must exist on the edge of the mbr
            {:line-segments [(line-segment point2 (first intersection-points))]})

          point1-in-mbr
          (if (approx= point1 (first intersection-points) COVERS_TOLERANCE)
            {:points [point1]} ; Point 1 must exist on the edge of the mbr
            {:line-segments [(line-segment point1 (first intersection-points))]})

          :else
          {:points intersection-points})))))

(defn subselect
  "Selects a smaller portion of the line segment using an mbr. Will return nil if the line segment
  is not within the mbr. Subselecting a line segment with an mbr can result in multiple line segments
  and points. The subselected data is returned as a map containing the keys :line-segments and
  :points with sequences of line segments and points respectively."
  [ls mbr]
  (apply merge-with concat
         (map (partial subselect-not-across-am ls)
              (m/split-across-antimeridian mbr))))

(extend-protocol d/DerivedCalculator
  cmr.spatial.line_segment.LineSegment
  (calculate-derived ^LineSegment [^LineSegment a] a))
