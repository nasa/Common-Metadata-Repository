(ns cmr.spatial.vector
  (:require [cmr.spatial.math :refer :all]
            [primitive-math]
            [cmr.common.dev.record-pretty-printer :as record-pretty-printer]))
(primitive-math/use-primitive-operators)

(defrecord Vector
  [
   ^double x
   ^double y
   ^double z])

(record-pretty-printer/enable-record-pretty-printing Vector)

(defn new-vector
  [x y z]
  (->Vector (double x) (double y) (double z)))

(defn length ^double [^Vector v]
  (let [x (.x v) y (.y v) z (.z v)]
    (sqrt (+ (* x x) (* y y) (* z z)))))

(defn normalize [^Vector v]
  (let [l (length v)
        x (.x v) y (.y v) z (.z v)]
    (if (= 0.0 l)
      (throw (Exception. "Cannot normalize a vector length 0"))
      (new-vector (/ x l)
                  (/ y l)
                  (/ z l)))))

(defn cross-product [^Vector v1 ^Vector v2]
  (let [x1 (.x v1) y1 (.y v1) z1 (.z v1)
        x2 (.x v2) y2 (.y v2) z2 (.z v2)

        px (- (* y1 z2) (* y2 z1))
        py (- (* z1 x2) (* z2 x1))
        pz (- (* x1 y2) (* y1 x2))]
    (new-vector px py pz)))

(defn opposite
  "Finds the vector pointing in the opposite direction."
  [^Vector v]
  (let [x (.x v) y (.y v) z (.z v)]
    (new-vector (* -1.0 x) (* -1.0 y) (* -1.0 z))))

(def ^:const ^double VECTOR_EQUAL_DELTA
  "Defines how close parts of a vector can be considered before they will be considered equal. This
  is roughly equivalent to a 0.9 meters on the surface of the Earth."
  0.0000001)

(defn- vector-approx=
  ([^Vector v1 ^Vector v2]
   (vector-approx= v1 v2 VECTOR_EQUAL_DELTA))
  ([^Vector v1 ^Vector v2 ^double delta]
   (let [x1 (.x v1) y1 (.y v1) z1 (.z v1)
         x2 (.x v2) y2 (.y v2) z2 (.z v2)]
     (and
       (double-approx= x1 x2 delta)
       (double-approx= y1 y2 delta)
       (double-approx= z1 z2 delta)))))

(defn parallel?
  "Returns true if two vectors are parallel. Two vectors are parallel if they point in the same direction
  or opposite directions. Assumes both vectors are normalized"
  [v1 v2]
  (or (vector-approx= v1 v2)
      (vector-approx= (opposite v1) v2)))




