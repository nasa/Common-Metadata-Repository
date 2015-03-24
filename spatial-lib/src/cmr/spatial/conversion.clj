(ns cmr.spatial.conversion
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.vector :as v]
            [cmr.spatial.math :refer :all]
            [primitive-math])
  (:import cmr.spatial.point.Point
           cmr.spatial.vector.Vector))
(primitive-math/use-primitive-operators)

(defn lon-lat-cross-product
  "A safer version of the cross product according to http://williams.best.vwh.net/intersect.htm
  It was originally these formulas. I've simplified it so that nothing is calculated twice
  * x = sin(lat1-lat2) * sin((lon1+lon2)/2) * cos((lon1-lon2)/2) - sin(lat1+lat2) * cos((lon1+lon2)/2) * sin((lon1-lon2)/2)
  * y = sin(lat1-lat2) * cos((lon1+lon2)/2) * cos((lon1-lon2)/2) + sin(lat1+lat2) * sin((lon1+lon2)/2) * sin((lon1-lon2)/2)
  * z = cos(lat1) * cos(lat2) * sin(lon1-lon2)"
  [^Point p1 ^Point p2]
  (let [lon1 (.lon-rad p1)
        lat1 (.lat-rad p1)
        lon2 (.lon-rad p2)
        lat2 (.lat-rad p2)

        b (/ (+ lon1 lon2) 2.0)
        sin-b (sin b)
        cos-b (cos b)

        c (/ (- lon1 lon2) 2.0)
        e (* (sin (- lat1 lat2)) (cos c))
        f (* (sin (+ lat1 lat2)) (sin c))

        x (- (* e sin-b) (* f cos-b))
        y (+ (* e cos-b) (* f sin-b))
        z (* (cos lat1) (cos lat2) (sin (- lon1 lon2)))]
    (v/new-vector x y z)))

(defn point->vector [^Point p]
  (let [lon-rad (.lon-rad p) lat-rad (.lat-rad p)
        cos-lat (cos lat-rad)
        x (* cos-lat (cos lon-rad))
        y (* -1.0 cos-lat (sin lon-rad))
        z (sin lat-rad)]
    (v/new-vector x y z)))

(defn vector->point [^Vector v]
  (let [x (.x v) y (.y v) z (.z v)
        lon-rad (atan2 (* -1.0 y) x)
        lat-rad (atan2 z (sqrt (+ (* x x) (* y y))))]
    (p/point (degrees lon-rad)
             (degrees lat-rad)
             lon-rad
             lat-rad)))
