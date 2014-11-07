(ns cmr.spatial.mbr
  (:require [cmr.spatial.math :as math :refer :all]
            [primitive-math]
            [cmr.spatial.point :as p]
            [cmr.spatial.derived :as d]
            [cmr.common.services.errors :as errors]
            [pjstadig.assertions :as pj]
            [cmr.spatial.validation :as v]
            [cmr.spatial.messages :as msg]
            [cmr.common.dev.record-pretty-printer :as record-pretty-printer])
  (:import cmr.spatial.point.Point))

(primitive-math/use-primitive-operators)

;; MBR - Minimum Bounding Rectangle
(defrecord Mbr
  [
   ^double west
   ^double north
   ^double east
   ^double south
  ])
(record-pretty-printer/enable-record-pretty-printing Mbr)

(defn mbr
  "Creates a new minimum bounding rectangle"
  [^double west ^double north ^double east ^double south]
  ;; Handle west or east being on the antimeridian.
  (let [am? #(= (abs %) 180.0)
        [west east] (cond
                      (and (am? west) (am? east))
                      (if (= west east)
                        [west east]
                        [-180.0 180.0])

                      ;; West should always be positive 180.0 if east isn't on AM.
                      (am? west) [-180.0 east]

                      ;; East should always be positive 180.0 if west isnt' on AM.
                      (am? east) [west 180.0]

                      :else [west east])]

    (->Mbr west north east south)))

(defn crosses-antimeridian? [^Mbr mbr]
  (> (.west mbr) (.east mbr)))

(def ^:const COVERS_TOLERANCE
  "Tolerance used for the covers method. Longitudes and latitudes technically outside the bounding rectangle
  but within this tolerance will be considered covered by the bounding rectangle"
  0.0000000001)

(defn point->mbr
  "Returns an mbr that covers only a single point"
  [point]
  (cond
    ;; It's important that this covers all longitudes at the pole. The function is used for creating
    ;; an MBR to represent a point in Elasticsearch. An MBR touching the north pole might miss the
    ;; point mbr if it didn't cover every longitude.
    (p/is-north-pole? point)
    (mbr -180 90 180 90)

    (p/is-south-pole? point)
    (mbr -180 -90 180 -90)

    :else
    (let [{:keys [lon lat]} point]
      (mbr lon lat lon lat))))

(defn- lon-range-covers-lon?
  "Returns true if lon is between west and east."
  [^double west ^double east ^double lon]
  (let [west (- west COVERS_TOLERANCE)
        east (+ east COVERS_TOLERANCE)
        crosses-antimeridian (> west east)]
    (cond
      crosses-antimeridian (or (>= lon west) (<= lon east))
      (= (abs lon) 180.0) (let [within-180 (- 180.0 COVERS_TOLERANCE)]
                            (or (>= (abs west) within-180)
                                (>= (abs east) within-180)))
      :else (and (>= lon west) (<= lon east)))))

(defn covers-lon?
  "Returns true if the mbr covers the given longitude"
  [^Mbr mbr ^double v]
  (let [west (.west mbr) east (.east mbr)]
    (lon-range-covers-lon? west east v)))

(defn covers-lat?
  "Returns true if the mbr covers the given latitude"
  [^Mbr mbr ^double v]
  (let [north (.north mbr) south (.south mbr)
        north (+ north COVERS_TOLERANCE)
        south (- south COVERS_TOLERANCE)]
    (and (>= v south) (<= v north))))

(defmulti covers-point?
  "Returns true if the mbr contains the given point"
  (fn [coord-sys mbr p]
    coord-sys))

(defmethod covers-point? :cartesian
  [coord-sys mbr ^Point p]
  (and (covers-lat? mbr (.lat p))
       (covers-lon? mbr (.lon p))))

(defmethod covers-point? :geodetic
  [coord-sys mbr ^Point p]
  (or
    (and (p/is-north-pole? p)
         (covers-lat? mbr 90.0))
    (and (p/is-south-pole? p)
         (covers-lat? mbr -90.0))
    (and (covers-lat? mbr (.lat p))
         (covers-lon? mbr (.lon p)))))

(defn corner-points
  "Returns the corner points of the mbr as upper left, upper right, lower right, lower left."
  [br]
  (let [{^double n :north ^double s :south ^double e :east ^double w :west} br]
    (p/ords->points w,n e,n e,s w,s)))

(defn split-across-antimeridian
  "Splits MBRs across the antimeridian. Returns a sequence of the mbrs if it crosses the antimeridian
  or a sequence containing original mbr."
  [m]
  (if (crosses-antimeridian? m)
    (let [{:keys [west north east south]} m]
      [(mbr west north 180.0 south)
       (mbr -180.0 north east south)])
    [m]))

(defn covers-mbr?
  "Returns true if the mbr completely covers the other-br."
  [coord-sys mbr other-br]
  (or (and (= (crosses-antimeridian? mbr)
              (crosses-antimeridian? other-br))
           (every? (partial covers-point? coord-sys mbr) (corner-points other-br)))

      ;; one crosses and one doesn't
      (and (crosses-antimeridian? mbr)
           (let [[c1 c2] (split-across-antimeridian mbr)]
             ;; Check to see if the mbr crosses the other br on either side of the antimeridian
             (or (covers-mbr? coord-sys c1 other-br)
                 (covers-mbr? coord-sys c2 other-br))))))

(defn center-point [m]
  (let [{^double n :north ^double s :south ^double e :east ^double w :west} m
        lat-center (mid s n)
        lon-center (mid-lon w e)]
    (p/point lon-center lat-center)))

(defn round-to-float-map
  "Converts a bounding rectangles values from double to float. It will round the bounding rectangle
  from double to float such that the bounding rectangle will slightly increase in size or decrease.
  The values are returned in a map since the Mbr record fields are type hinted as double."
  [m increase?]
  (let [{:keys [west north east south]} m
        max-lon (float 180)
        min-lon (float -180)
        max-lat (float 90)
        min-lat (float -90)
        [new-west new-east] (if (and (= east west) (not increase?))
                              ;; We can't shrink between west and east anymore
                              [(float west) (float east)]
                              [(double->float west (not increase?))
                               (double->float east increase?)])
        [new-south new-north] (if (and (= north south) (not increase?))
                                ;; We can't shrink between south and north anymore
                                [(float south) (float north)]
                                [(double->float south (not increase?))
                                 (double->float north increase?)])]
    {:west (math/constrain ^float new-west min-lon max-lon)
     :north (math/constrain ^float new-north min-lat max-lat)
     :east (math/constrain ^float new-east min-lon max-lon)
     :south (math/constrain ^float new-south min-lat max-lat)}))

(def whole-world
  "an mbr that covers the whole world"
  (mbr -180 90 180 -90))

(defn whole-world?
  "Returns true if an mbr covers the whole world"
  [mbr]
  (= mbr whole-world))

(def whole-world-square-degrees
  "The number of square degrees in the world"
  ^double (* 360.0 180.0))

(defn single-point?
  "Returns true if the MBR only covers a single point."
  [mbr]
  (and (= (:west mbr) (:east mbr))
       (= (:north mbr) (:south mbr))))

(defn percent-covering-world
  "Returns percentage in square lat lons that the MBR covers the world"
  ^double [^Mbr mbr]
  (let [w (.west mbr)
        n (.north mbr)
        e (.east mbr)
        s (.south mbr)
        lat-size (- n s)
        lon-size (if (crosses-antimeridian? mbr)
                   (+ (- 180.0 w) (- e -180.0))
                   (- e w))
        square-degrees (* lat-size lon-size)]
    (* 100.0 (/ square-degrees ^double whole-world-square-degrees))))

(defn external-points
  "Returns 3 points that are external to the mbr."
  [^Mbr mbr]
  (pj/assert (not (whole-world? mbr)))
  (let [w (.west mbr)
        n (.north mbr)
        e (.east mbr)
        s (.south mbr)
        ;; Finds three points within the area indicated
        points-in-area (fn [w n e s]
                         ; w n e s should define an area not crossing antimeridian
                         ;; Find mid lon of range then find mid lon on left and right
                         ;; use mid lat for all three points
                         (let [mid-lon (mid w e)
                               right-lon (mid w mid-lon)
                               left-lon (mid mid-lon e)
                               mid-lat (mid n s)]
                           [(p/point left-lon mid-lat)
                            (p/point mid-lon mid-lat)
                            (p/point right-lon mid-lat)]))
        crosses-antimeridian (crosses-antimeridian? mbr)

        ;; Find the biggest area around the MBR to use to find external points
        north-dist (- 90.0 n)
        south-dist (- s -90.0)
        west-dist (if crosses-antimeridian 0.0 (- w -180.0))
        east-dist (if crosses-antimeridian 0.0 (- 180.0 e))
        biggest-dist (max north-dist south-dist west-dist east-dist)]

    (cond
      (= biggest-dist north-dist) (points-in-area -180.0 90.0 180.0 n)
      (= biggest-dist south-dist) (points-in-area -180.0 s 180.0 -90.0)
      (and (not crosses-antimeridian)
           (= biggest-dist west-dist)) (points-in-area -180.0 90.0 w -90.0)
      (and (not crosses-antimeridian)
           (= biggest-dist east-dist)) (points-in-area e 90.0 180.0 -90.0)
      (crosses-antimeridian? mbr) (points-in-area e 90.0 w -90.0)
      :else (errors/internal-error!
              (str
                "Logic error: One of the other distances should have been largest it "
                "should have crossed the antimeridian: "
                (pr-str mbr))))))

(defn intersects-br?
  "Returns true if the mbr intersects the other bounding rectangle"
  [coord-sys ^Mbr mbr ^Mbr other-br]
  (some identity
        (for [m1 (split-across-antimeridian mbr)
              m2 (split-across-antimeridian other-br)]
          (or (some (partial covers-point? coord-sys m1) (corner-points m2))
              (some (partial covers-point? coord-sys m2) (corner-points m1))

              ;; Do they form an overlapping t shape?
              (let [{^double w1 :west ^double  n1 :north ^double e1 :east ^double s1 :south} m1
                    {^double w2 :west ^double  n2 :north ^double e2 :east ^double s2 :south} m2]
                (or (and (< w1 w2)
                         (> e1 e2)
                         (> n2 n1)
                         (< s2 s1))
                    (and (< w2 w1)
                         (> e2 e1)
                         (> n1 n2)
                         (< s1 s2))))))))

(defn non-crossing-intersects-br?
  "Specialized version of intersects-br? for two mbrs that don't cross the antimeridian.
  Returns true if the mbr intersects the other bounding rectangle."
  [^Mbr m1 ^Mbr m2]
  (pj/assert (not (or (crosses-antimeridian? m1)
                      (crosses-antimeridian? m2))))
  (let [{^double w1 :west ^double  n1 :north ^double e1 :east ^double s1 :south} m1
        {^double w2 :west ^double  n2 :north ^double e2 :east ^double s2 :south} m2
        range-intersects? (fn [s1 e1 s2 e2]
                            (or (within-range? s2 s1 e1)
                                (within-range? e2 s1 e1)
                                (within-range? s1 s2 e2)))]
    (and (range-intersects? w1 e1 w2 e2)
         (range-intersects? s1 n1 s2 n2))))


(defn intersections
  "Returns the intersection of the two minimum bounding rectangles. This could return multiple mbrs
  if one crosses the antimeridian and the other intersects both sides."
  [^Mbr m1 ^Mbr m2]
  (filter identity
          (for [m1-sub (split-across-antimeridian m1)
                m2-sub (split-across-antimeridian m2)]
            (when (non-crossing-intersects-br? m1-sub m2-sub)
              (let [{^double w1 :west ^double n1 :north ^double e1 :east ^double s1 :south} m1-sub
                    {^double w2 :west ^double n2 :north ^double e2 :east ^double s2 :south} m2-sub
                    new-west (max w1 w2)
                    new-east (min e1 e2)
                    new-north (min n1 n2)
                    new-south (max s1 s2)]
                (mbr new-west new-north new-east new-south))))))

(defn union
  "Returns the union of the minimum bounding rectangles. Accepts an optional argument to disable
  crossing the antimeridian. That argument only makes sense if none of the input mbrs cross the
  antimeridian."
  ([m1 m2]
   (union m1 m2 true))
  ([^Mbr m1 ^Mbr m2 allow-cross-antimeridian?]
   (pj/assert (or allow-cross-antimeridian?
                  (not (or (crosses-antimeridian? m1)
                           (crosses-antimeridian? m2))))
              "allow-cross-antimeridian? was false and either m1 or m2 crossed the antimeridian")
   (let [;; lon range union
         [w e] (cond
                 ;; both cross antimeridian
                 (and (crosses-antimeridian? m1) (crosses-antimeridian? m2))
                 (let [w (min (.west m1) (.west m2))
                       e (max (.east m1) (.east m2))]
                   (if (<= w e)
                     ;; If the result covers the whole world then we'll set it to that.
                     [-180.0 180.0]
                     [w e]))

                 ;; one crosses the antimeridian
                 (or (crosses-antimeridian? m1) (crosses-antimeridian? m2))
                 ;; Make m1 cross the antimeridian
                 (let [[^Mbr m1 ^Mbr m2] (if (crosses-antimeridian? m2)
                                           [m2 m1]
                                           [m1 m2])
                       w1 (.west m1) e1 (.east m1)
                       w2 (.west m2) e2 (.east m2)
                       ;; We could expand m1 to the east or to the west. Pick the shorter of the two.
                       west-dist (- w1 w2)
                       east-dist (- e2 e1)
                       [^double w ^double e] (cond
                                               (or (<= west-dist 0.0) (<= east-dist 0.0)) [w1 e1]
                                               (< east-dist west-dist) [w1 e2]
                                               :else [w2 e1])]

                   (if (<= w e)
                     ;; If the result covers the whole world then we'll set it to that.
                     [-180.0 180.0]
                     [w e]))

                 ;; none cross the antimeridian
                 :else
                 (let [[^Mbr m1 ^Mbr m2] (if (> (.west m1) (.west m2))
                                           [m2 m1]
                                           [m1 m2])
                       w1 (.west m1) e1 (.east m1)
                       w2 (.west m2) e2 (.east m2)
                       w (min w1 w2)
                       e (max e1 e2)

                       ;; Check if it's shorter to cross the antimeridian
                       dist (- e w)
                       alt-west w2
                       alt-east e1
                       alt-dist (+ (- 180.0 alt-west) (- alt-east -180.0))]
                   (if (and allow-cross-antimeridian? (< alt-dist dist))
                     [alt-west alt-east]
                     [w e])))

         ;; lat range union
         n (max (.north m1) (.north m2))
         s (min (.south m1) (.south m2))]
     (mbr w n e s))))

(extend-protocol d/DerivedCalculator
  cmr.spatial.mbr.Mbr
  (calculate-derived
    ^Mbr [^Mbr mbr]
    mbr))

(extend-protocol v/SpatialValidation
  cmr.spatial.mbr.Mbr
  (validate
    [{:keys [^double north ^double south]}]
      (when (< north south)
        [(msg/br-north-less-than-south north south)])))
