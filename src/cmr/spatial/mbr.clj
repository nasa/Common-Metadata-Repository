(ns cmr.spatial.mbr
  (:require [cmr.spatial.math :refer :all]
            [primitive-math]
            [cmr.spatial.point :as p]
            [cmr.spatial.derived :as d]
            [cmr.common.services.errors :as errors]
            [pjstadig.assertions :as pj])
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
  0.000001)

(defn covers-lon? [^Mbr mbr ^double v]
  (let [west (.west mbr) east (.east mbr)
        west (- west COVERS_TOLERANCE)
        east (+ east COVERS_TOLERANCE)]
    (cond
      (crosses-antimeridian? mbr) (or (>= v west) (<= v east))
      (= (abs v) 180.0) (let [within-180 (- 180.0 COVERS_TOLERANCE)]
                          (or (>= (abs west) within-180)
                              (>= (abs east) within-180)))
      :else (and (>= v west) (<= v east)))))

(defn covers-lat? [^Mbr mbr ^double v]
  (let [north (.north mbr) south (.south mbr)
        north (+ north COVERS_TOLERANCE)
        south (- south COVERS_TOLERANCE)]
    (and (>= v south) (<= v north))))

(defn covers-point?
  "Returns true if the mbr contains the given point"
  [mbr ^Point p]
  (or
    (and (p/is-north-pole? p)
         (covers-lat? mbr 90.0))
    (and (p/is-south-pole? p)
         (covers-lat? mbr -90.0))
    (and (covers-lat? mbr (.lat p))
         (covers-lon? mbr (.lon p)))))

(defn corner-points
  "Returns the corner points of the mbr"
  [br]
  (let [{^double n :north ^double s :south ^double e :east ^double w :west} br]
    (p/ords->points w,n e,n e,s w,s)))

(defn center-point [m]
  (let [{^double n :north ^double s :south ^double e :east ^double w :west} m
        lat-center (mid s n)
        lon-center (mid-lon w e)]
    (p/point lon-center lat-center)))

(defn split-across-antimeridian
  "Splits MBRs across the antimeridian. Returns a sequence of the mbrs if it crosses the antimeridian
  or a sequence containing original mbr."
  [m]
  (if (crosses-antimeridian? m)
    (let [{:keys [west north east south]} m]
      [(mbr west north 180.0 south)
       (mbr -180.0 north east south)])
    [m]))

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


(defn union [^Mbr m1 ^Mbr m2]
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
                                           ;(sort-by #(.west ^Mbr %) [m1 m2])
                      w1 (.west m1) e1 (.east m1)
                      w2 (.west m2) e2 (.east m2)
                      w (min w1 w2)
                      e (max e1 e2)

                      ;; Check if it's shorter to cross the antimeridian
                      dist (- e w)
                      alt-west w2
                      alt-east e1
                      alt-dist (+ (- 180.0 alt-west) (- alt-east -180.0))]
                  (if (< alt-dist dist)
                    [alt-west alt-east]
                    [w e])))

        ;; lat range union
        n (max (.north m1) (.north m2))
        s (min (.south m1) (.south m2))]
    (mbr w n e s)))

(extend-protocol d/DerivedCalculator
  cmr.spatial.mbr.Mbr
  (calculate-derived
    ^Mbr [^Mbr mbr]
    mbr))
