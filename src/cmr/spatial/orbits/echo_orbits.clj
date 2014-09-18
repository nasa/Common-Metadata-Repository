(ns cmr.spatial.orbits.echo-orbits
  "TODO"
  (:require [cmr.spatial.math :refer :all]
            [cmr.common.services.errors :as errors]
            [primitive-math]
            [clj-time.core :as t]))

(primitive-math/use-primitive-operators)

(def ^:const ^double SOLAR_DAY_S
  (* 24.0 3600.0))

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
     :number-of-orbits 1.0})

)

(defn angular-velocity-rad-s
  ^double [orbit-parameters]
  (/ TAU (* 60.0 ^double (:period orbit-parameters))))

(defn swath-width-rad
  ^double [orbit-parameters]
  (/ (* ^double (:swath-width orbit-parameters) 1000.0) EARTH_RADIUS_METERS))

(defn inclination-rad
  ^double [orbit-parameters]
  (radians (:inclination-angle orbit-parameters)))

(defn retrograde?
  [orbit-parameters]
  (> (radians (:inclination-angle orbit-parameters)) (/ PI 2.0)))