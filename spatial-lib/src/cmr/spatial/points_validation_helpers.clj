(ns cmr.spatial.points-validation-helpers
  "Defines functions for validating shapes with points."
  (:require [cmr.spatial.math :refer :all]
            [primitive-math]
            [cmr.spatial.validation :as v]
            [cmr.spatial.messages :as msg]
            [cmr.spatial.point :as p]))
(primitive-math/use-primitive-operators)

(defn points-in-shape-validation
  "Validates the individual points of a shape"
  [{:keys [points]}]
  (mapcat (fn [[i point]]
            (when-let [errors (v/validate point)]
              (map (partial msg/shape-point-invalid i) errors)))
          (map-indexed vector points)))

(defn points->rounded-point-map
  "Combines together points that round to the same value. Takes a sequence of points and returns a
  map of rounded points to list of index, point pairs."
  [points]
  (reduce (fn [m [i point]]
            (let [rounded (p/round-point 8 point)]
              (update-in m [rounded] conj [i point])))
          {}
          (map-indexed vector points)))

(defn duplicate-point-validation
  "Validates that the ring does not contain any duplicate or very close together points."
  [{:keys [points]}]

  ;; Create a map of the rounded points to a list of points that round that same value. If any of the
  ;; rounded points has more than one other point in the list then there are duplicates.
  (let [rounded-point-map (points->rounded-point-map points)
        duplicate-point-lists (->> rounded-point-map
                                   vals
                                   (filter #(> (count %) 1))
                                   ;; reversing lists of duplicate points to put points in indexed order
                                   ;; for more pleasing messages.
                                   (map reverse))]
    (map msg/duplicate-points duplicate-point-lists)))

(defn consecutive-antipodal-points-validation
  "Validates that the ring does not have any consecutive antipodal points"
  [{:keys [points]}]

  (let [indexed-points (map-indexed vector points)
        indexed-point-pairs (partition 2 1 indexed-points)
        antipodal-indexed-point-pairs (filter (fn [[[_ p1] [_ p2]]]
                                                (p/antipodal? p1 p2))
                                              indexed-point-pairs)]
    (map (partial apply msg/consecutive-antipodal-points)
         antipodal-indexed-point-pairs)))
