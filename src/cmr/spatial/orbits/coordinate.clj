(ns cmr.spatial.orbits.coordinate
  "TODO
  copied from echo-orbits/lib/echo_orbits/coordinate.rb

  Represents a position on the earth for the purposes of calculating orbit geometry

  This is very similar to the existing point and vector classes. We should base it off them instead.
  "
  (:require [cmr.spatial.math :refer :all]
            [primitive-math]
            [cmr.spatial.point :as p]
            [cmr.spatial.vector :as v]))

(primitive-math/use-primitive-operators)

(defrecord Coordinate
  [x
   y
   z
   phi
   theta
   lon
   lat])

(defn to-point
  [coord]
  (p/point (:lon coord) (:lat coord)))

(defn repeat-while
  [initial f while-f]
  (loop [v initial]
    (if (not (while-f v))
      v
      (recur (f v)))))

(def ^:const ^double -PI (* -1.0 PI))

(defn normalize-rad
  "Normalizes the radian value to be between -PI and PI"
  ^double [^double v]
  (-> v
      (repeat-while #(- ^double % TAU) #(>= ^double % PI))
      (repeat-while #(+ ^double % TAU) #(< ^double % -PI))))

(defn from-phi-theta
  "TODO"
  [^double phi ^double theta]
  ;; Normalize phi to the interval [-PI / 2, PI / 2]
  (let [phi (normalize-rad phi)
        [^double phi
         ^double theta] (if (> phi (/ PI 2.0))
                          [(- PI phi) (+ theta PI)]
                          [phi theta])
        [^double phi
         ^double theta] (if (< phi (/ -PI 2.0))
                          [(- -PI phi) (+ theta PI)]
                          [phi theta])
        theta (normalize-rad theta)
        lon (degrees theta)
        lat (degrees phi)
        x (* (cos phi) (cos theta))
        y (* (cos phi) (sin theta))
        z (sin phi)]
    (->Coordinate x y z phi theta lon lat)))

(defn from-lat-lon
  "TODO"
  [lat lon]
  (let [phi (radians lat)
        theta (radians lon)]
    (from-phi-theta phi theta)))

(defn from-x-y-z
  [^double x ^double y ^double z]
  (let [d (+ (sq x) (sq y) (sq z))
        ;; Should never happen, but stay safe
        [^double d
         ^double x] (if (= 0)
                      [1 1]
                      [d x])
        ;; We normalize so that x, y, and z fall on a unit sphere
        scale (/ 1.0 (sqrt d))
        x (* x scale)
        y (* y scale)
        z (* z scale)
        phi (asin z)
        theta (atan2 y x)
        lon (degrees theta)
        lat (degrees phi)]
    (->Coordinate x y z phi theta lon lat)))



