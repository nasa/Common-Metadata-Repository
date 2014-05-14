(ns cmr.spatial.serialize
  "Contains functions for serializing ordinates for storage in an index"
  (:require [cmr.common.services.errors :as errors]
            [cmr.spatial.ring :as r]
            [cmr.spatial.math :refer :all]
            [cmr.spatial.polygon :as poly]))


;; Some thoughts about how to store the elasticsearch data in a way that preserves space and accuracy.
(comment
  ;; 10 meters of accuracy at equator is provided by 0.0001 degrees
  ;; 123.4567 is the space of something.
  ;; Multiplying * 10000
  (== (* 123.4567 10000) 1234567)

  ;; Short is too small
  ;                1234567
  (= Short/MAX_VALUE 32767)

  ;; Integer is big enough and then some
  ;                       1234567
  (= Integer/MAX_VALUE 2147483647)

  (defn maintains-precision?
    [v]
    (= v
       (stored->ordinate (ordinate->stored v))))

  ;; We could store the largest longitude as an Integer
  (< (ordinate->stored 180) Integer/MAX_VALUE)
  (> (ordinate->stored -180) Integer/MIN_VALUE)

  ;; The maximum accuracy we could maintain is 7 digits
  (= true (maintains-precision? 179.1234567))
  (= true (maintains-precision? -179.1234567))

  ;; Any thing after that loses precision
  (= false (maintains-precision? 179.12345678)))

(def multiplication-factor
  "Ordinates are stored as integers in elasticsearch to maintain space. This is the number used
  to convert the ordinate to and from an integer such that the largest ordinate fits in integer
  space and maintains as much accuracy as possible"
  10000000)

(defn ordinate->stored
  "Converts an ordinate value into an integer for storage"
  [v]
  (int (round 0 (* v multiplication-factor))))

(defn stored->ordinate
  "Converts an stored ordinate value into a double for spatial calculations"
  [v]
  (double (/ v multiplication-factor)))

(defprotocol ShapeStoredOrdinatesConversion
  (shape->stored-ords
    [shape]
    "Converts a spatial shape into the ordinates to ordinates to store in the search index"))

(extend-protocol ShapeStoredOrdinatesConversion
  cmr.spatial.polygon.Polygon
  (shape->stored-ords
    [{:keys [rings]}]
    ;; only supports single ring polygons for now
    (when (> (count rings) 1)
      (errors/internal-error! "shape->stored-ords only supports polygons with a single ring. TODO add support"))
    (map ordinate->stored (r/ring->ords (first rings)))))

(defmulti stored-ords->shape
  "Converts a type and stored ordinates into a spatial shape"
  (fn [type ords]
    type))

(defmethod stored-ords->shape :polygon
  [type ords]
  (poly/polygon [(apply r/ords->ring (map stored->ordinate ords))]))
