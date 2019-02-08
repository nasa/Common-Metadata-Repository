(ns cmr.spatial.orbits.orbits
  "Useful constants and transformation for orbit parameters"
  (:require
   [clj-time.core :as t]
   [cmr.common.services.errors :as errors]
   [cmr.spatial.math :refer :all]
   [primitive-math]))

(primitive-math/use-primitive-operators)

(comment

  (def example-orbit-parameters
    {
     ;; The number of degrees of inclination for the orbit
     :inclination-angle 98.0
     ;; The number of minutes it takes to complete one orbit
     :period 97.87
     ;; The width of the orbital track in kilometers
     :swath-width 390.0
     ;; The starting circular latitude in degrees
     :start-circular-latitude -90.0
     ;;The number of orbits per granule of data (may be a fraction)
     :number-of-orbits 1.0}))



(defn angular-velocity-rad-s
  "Returns the orbit's angular velocity in radians/second"
  ^double [orbit-parameters]
  (/ TAU (* 60.0 ^double (:period orbit-parameters))))

(defn swath-width-rad
  "Returns the orbit's swath width in radians"
  ^double [orbit-parameters]
  (/ (* ^double (:swath-width orbit-parameters) 1000.0) EARTH_RADIUS_METERS))

(defn inclination-rad
  "Returns the orbit's inclination in radians"
  ^double [orbit-parameters]
  (radians (:inclination-angle orbit-parameters)))

(defn declination-rad
  "Returns the orbit's declination in radians"
  ^double [orbit-parameters]
  (- ^double (inclination-rad orbit-parameters) (/ PI 2.0)))
