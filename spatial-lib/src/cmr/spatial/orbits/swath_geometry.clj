(ns cmr.spatial.orbits.swath-geometry
  "Functions for transforming orbit parameters into useful geometries"
  (:require
   [clj-time.coerce :as c]
   [clj-time.core :as t]
   [clojure.core.matrix :as cm]
   [cmr.common.services.errors :as errors]
   [cmr.spatial.geodetic-ring :as gr]
   [cmr.spatial.math :refer :all]
   [cmr.spatial.orbits.orbits :as orbits]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]
   [mikera.vectorz.core :as v]
   [mikera.vectorz.matrix :as m]
   [primitive-math])
  (:import (mikera.matrixx AMatrix)))

;; This module constructs orbit geometries as strings of lat/lon edge points by
;; using rotation matrices.  It helps to take a second to visualize the rotations.
;; (It'd help more if I were any good with animation.)
;;
;; Imagine a coordinate system centered on the Earth.
;;   - The positive x axis passes through lat/lon (0, 0)
;;   - The z axis passes through the poles
;;   - the y axis is perpendicular to the x and z axes
;;
;; Take an orbit for a granule with an ascending crossing longitude and time, a
;; temporal range, and collection orbit parameters including period, declination,
;; and swath width.
;;
;; Perform the following steps, imagining the position of the vector, v, touching
;; lat/lon (0, 0) at each step.
;; 1. Rotate the coordinate system around the z-axis by the granule's ascending
;;    crossing longitude.  v is now at lat/lon (0, ascending-crossing-lon)
;; 2. Rotate the coordinate system around the x-axis by the orbit's declination,
;;    which is a typically small number of degrees describing how the orbit is
;;    tilted with respect to the poles.  v doesn't move.
;; 3. For a desired time, t, expressed in seconds offset from the orbit's ascending
;;    crossing time:
;;    a. Determine how many degrees the orbit progresses in time t, based on the
;;       orbit's period.  Rotate that many degrees around the y axis.  v now points
;;       at a position along the orbit's track, uncorrected for the Earth's rotation.
;;    b. Determine how many degrees the Earth rotates in time t.  Rotate backward
;;       that many degrees around the *original* z axis (the axis passing through
;;       the poles).  v now points at the ground-track position of the satellite at
;;       time t.
;;    c. To obtain swath edges, rotate around the z axis +/- half the swath width
;;       in radians.  v now points to the edge of the swath.
;;    d. Multiply the resulting transformation matrices for the swath edges by
;;       <1, 0, 0> to get edge positions.
;;
;; Repeat step 3 for several sample times within the orbit's temporal range at a
;; granularity that balances performance with accuracy to build up lists of swath
;; edge positions.

(primitive-math/use-primitive-operators)

(defn- ascending-crossing-time
  "Finds the ascending crossing date time given at the given longitude from orbit calculated spatial domains"
  [orbit-parameters ascending-crossing-lon ocsds]
  ;; If the ascending equator crossing has an entry in the spatial domain, then we use the time
  ;; corresponding to it for the ascending equator crossing time, otherwise we assume that the first
  ;; equator crossing in the spatial domain is a descending crossing and the ascending equator
  ;; crossing occurred half period earlier.
  (if-let [match (first (filter #(= ascending-crossing-lon
                                    (:equator-crossing-longitude %))
                                ocsds))]
    (c/from-string (:equator-crossing-date-time match))
    (t/minus (c/from-string (:equator-crossing-date-time (first ocsds)))
             (t/minutes (/ ^double (:period orbit-parameters) 2.0)))))

(defn- to-unix-time
  "Returns the time, in seconds, since the previous Epoch"
  ^long [datetime]
  (/ ^long (c/to-long datetime) 1000))

(defn- temporal-offset-range
  "Given a temporal range and an ascending crossing datetime, returns a list of
  two items containing the temporal start and end dates expressed as seconds
  offset from the ascending crossing time"
  [start-date end-date ascending-crossing-date]
  (let [begin-s (to-unix-time start-date)
        end-s (to-unix-time end-date)
        crossing-s (to-unix-time ascending-crossing-date)]
    [(- begin-s crossing-s) (- end-s crossing-s)]))

(defn- vec3->point
  "Transforms a vectorz x,y,z coordinate to a CMR lat/lon point"
  [vec3]
  (let [[x y z] (v/to-list vec3)]
    (p/point (degrees (atan2 y x)) (degrees (asin z)))))

(defn- earth-rotation-transform
  "Returns a rotation matrix around the z-axis by an amount equal to the Earth's
  rotation in t seconds"
  [^double t]
  (m/z-axis-rotation-matrix (- (* EARTH_ANGULAR_VELOCITY_RAD_S t))))

(defn- orbit-latitude-transform
  "Returns a rotation matrix around the y-axis (orbit track) by the given number
  of radians"
  [^double circular-latitude]
  (m/y-axis-rotation-matrix (- circular-latitude)))

(defn- orbit-declination-transform
  "Returns a rotation matrix around the x-axis by the given orbit's declination"
  [orbit-parameters]
  (m/x-axis-rotation-matrix (orbits/declination-rad orbit-parameters)))

(defn- orbit-crossing-theta-transform
  "Returns a rotation matrix around the z-axis by the given orbit's ascending
  equatorial crossing, given in radians"
  [ascending-crossing-theta]
  (m/z-axis-rotation-matrix ascending-crossing-theta))

(defn- fn-time->track-position-transform
  "Given an orbit and an ascending equatorial crossing (in radians), returns
  a function which takes a time offset in seconds and returns a matrix
  which will transform the vector <1, 0, 0> into the <x, y, z> position
  along the orbit track at the given time."
  [orbit-parameters ascending-crossing-theta]
  (let [orientation-transform (cm/mmul (orbit-crossing-theta-transform ascending-crossing-theta)
                                       (orbit-declination-transform orbit-parameters))
        angular-velocity-rad-s (orbits/angular-velocity-rad-s orbit-parameters)]
    (fn [^double t]
      (cm/mmul
        (earth-rotation-transform t)
        orientation-transform
        (orbit-latitude-transform (* angular-velocity-rad-s t))))))

(defn- fn-time->swath-edges
  "Given an orbit and an ascending equatorial crossing (in radians), returns
  a function which takes a time offset in seconds and returns a two-element
  array containing the left and right lat/lon swath edge points at the
  given time"
  [orbit-parameters ascending-crossing-theta]
  (let [time->track-position-transform (fn-time->track-position-transform orbit-parameters ascending-crossing-theta)
        half-swath-width-rad (/ (orbits/swath-width-rad orbit-parameters) 2.0)
        left-swath-transform (m/z-axis-rotation-matrix (- half-swath-width-rad))
        right-swath-transform (m/z-axis-rotation-matrix half-swath-width-rad)
        x-axis (cm/array :vectorz [1.0 0.0 0.0])]
    (fn [t]
      (let [track-position-transform (time->track-position-transform t)]
        (map vec3->point
             [(cm/mmul track-position-transform left-swath-transform x-axis)
              (cm/mmul track-position-transform right-swath-transform x-axis)])))))

(defn- interpolation-times
  "Returns a sequence of time offsets (in seconds) starting at start-time-s and
  ending at end-time-s, separated by at most interval-separation-s seconds.  The
  start and end time offsets will be included in the resulting sequence."
  [start-time-s end-time-s interval-separation-s]
  (concat (range start-time-s end-time-s interval-separation-s) [end-time-s]))

(defn to-swaths
  "Returns a list of tuples corresponding to the left and right swath edges. Each tuple
  is a pair of lon/lat tuples corresponding to points on the left and right edge of the swath.
  The elements in the sequence are separated by at most interval-separation-s
  seconds (default: 300), one outer sequence per orbit revolution.

  Parameters:
  orbit-parameters: the granule's collection-level orbit metadata
  ascending-crossing-lon: the granule's ascending crossing lon
  ocsds: a sequence of the granule's orbit calculated spatial domain elements
  start-date: the granule's temporal start date
  end-date: the granule's temporal end date
  interval-separation-s: the maximum desired time sparation in seconds
  between two edge points"
  ([orbit-parameters ascending-crossing-lon ocsds start-date end-date]
   (to-swaths orbit-parameters ascending-crossing-lon ocsds start-date end-date 300.0))
  ([orbit-parameters ascending-crossing-lon ocsds start-date end-date interval-separation-s]
   (let [ascending-crossing-date (ascending-crossing-time orbit-parameters ascending-crossing-lon ocsds)
         [start-time-s end-time-s] (temporal-offset-range start-date end-date ascending-crossing-date)
         ascending-crossing-theta (radians ascending-crossing-lon)
         time->swath-edges (fn-time->swath-edges orbit-parameters ascending-crossing-theta)
         orbit-starts (interpolation-times start-time-s end-time-s (* ^double (:period orbit-parameters) 60.0))
         orbit-time-ranges (map vector orbit-starts (rest orbit-starts))
         times (map (fn [[start end]] (interpolation-times start end interval-separation-s)) orbit-time-ranges)]
     (map (partial map time->swath-edges) times))))

(defn- to-polygon
  "Swath is a sequence of vectors containing
  [left-swath-edge-point right-swath-edge-point]"
  [swath]
  (let [ring (concat (map first swath)
                     (reverse (map second swath))
                     [(ffirst swath)])]
    (poly/polygon :geodetic [(gr/ring (doall ring))])))

(defn to-polygons
  [& args]
  (map to-polygon (apply to-swaths args)))

;; Tests
(comment

  (defn to-polygons
    [& args]
    (let [swath (apply to-swath args)
          ring (concat (map first swath)
                       (reverse (map second swath))
                       [(ffirst swath)])]
      (list (poly/polygon :geodetic [(gr/ring ring)]))))

  ; Based on G1000781596-NSIDC_ECS, 14 tiny orbits
  (defn test-orbit-to-poly
    []
    (let [orbit-parameters {:inclination-angle 94.0 :period 96.7 :swath-width 2.0 :start-circular-latitude 50.0 :number-of-orbits 14.0}
          ocsds [{:orbit-number 579 :equator-crossing-longitude  104.08523   :equator-crossing-date-time (t/date-time 2003  2 20 20 25 19)}
                 {:orbit-number 580 :equator-crossing-longitude   79.88192   :equator-crossing-date-time (t/date-time 2003  2 20 22  2  0)}
                 {:orbit-number 581 :equator-crossing-longitude   55.67938   :equator-crossing-date-time (t/date-time 2003  2 20 23 38 39)}
                 {:orbit-number 582 :equator-crossing-longitude   31.481928  :equator-crossing-date-time (t/date-time 2003  2 21  1 15 19)}
                 {:orbit-number 583 :equator-crossing-longitude    7.2811584 :equator-crossing-date-time (t/date-time 2003  2 21  2 52  0)}
                 {:orbit-number 584 :equator-crossing-longitude  -16.919495  :equator-crossing-date-time (t/date-time 2003  2 21  4 28 40)}
                 {:orbit-number 585 :equator-crossing-longitude  -41.121002  :equator-crossing-date-time (t/date-time 2003  2 21  6  5 19)}
                 {:orbit-number 586 :equator-crossing-longitude  -65.32291   :equator-crossing-date-time (t/date-time 2003  2 21  7 42  7)}
                 {:orbit-number 587 :equator-crossing-longitude  -89.52368   :equator-crossing-date-time (t/date-time 2003  2 21  9 18 48)}
                 {:orbit-number 588 :equator-crossing-longitude -113.72504   :equator-crossing-date-time (t/date-time 2003  2 21 10 55 28)}
                 {:orbit-number 589 :equator-crossing-longitude -137.92719   :equator-crossing-date-time (t/date-time 2003  2 21 12 32  7)}
                 {:orbit-number 590 :equator-crossing-longitude -162.12923   :equator-crossing-date-time (t/date-time 2003  2 21 14  8 48)}
                 {:orbit-number 591 :equator-crossing-longitude  173.66837   :equator-crossing-date-time (t/date-time 2003  2 21 15 45 28)}
                 {:orbit-number 592 :equator-crossing-longitude  149.46652   :equator-crossing-date-time (t/date-time 2003  2 21 17 22  7)}
                 {:orbit-number 593 :equator-crossing-longitude  125.265396  :equator-crossing-date-time (t/date-time 2003  2 21 18 58 47)}]
          ascending-crossing-lon 104.0823
          start-date (t/date-time 2003 2 20 21 48 32)
          end-time (t/date-time 2003 2 21 19 12 15)]

      (with-progress-reporting
        (bench (to-polygons orbit-parameters ascending-crossing-lon ocsds start-time end-time))))))

(comment
  ; Based on G195170098-GSFCS4PA and G195170099-GSFCS4PA.  These should line up one against the other
  (defn test-orbit-to-poly-2
    []
    (let [orbit-parameters {:inclination-angle 98.2 :period 100.0 :swath-width 2600.0 :start-circular-latitude 0.0 :number-of-orbits 1.0}
          crossing-lon0 -167.57
          ocsds0 [{:orbit-number 1132 :equator-crossing-longitude  -167.57   :equator-crossing-date-time (t/date-time 2004 10  1  0 52 22)}]
          start-date0 (t/date-time 2004 10 1 0  3  5)
          end-date0 (t/date-time 2004 10 1 1 41 58)

          crossing-lon1 167.71
          ocsds1 [{:orbit-number 1133 :equator-crossing-longitude 167.71   :equator-crossing-date-time (t/date-time 2004 10  1  2 31 15)}]
          start-date1 (t/date-time 2004 10 1 1 41 58)
          end-date1 (t/date-time 2004 10 1 3 20 50)]

      (let [geo0 (to-polygons orbit-parameters crossing-lon0 ocsds0 start-time0 end-time0)
            geo1 (to-polygons orbit-parameters crossing-lon1 ocsds1 start-time1 end-time1)]
        (viz-helper/clear-geometries)
        (viz-helper/add-geometries geo0)
        (viz-helper/add-geometries geo1)
        [geo0 geo1]))))
