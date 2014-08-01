(ns cmr.spatial.geodetic-ring-validations
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.math :refer :all]
            [cmr.common.util :as util]
            [primitive-math]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.conversion :as c]
            [cmr.spatial.arc :as a]
            [cmr.spatial.derived :as d]
            [cmr.spatial.geodetic-ring :as gr]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.validation :as v]
            [cmr.spatial.messages :as msg])
  (:import cmr.spatial.arc.Arc))
(primitive-math/use-primitive-operators)

(defn- ring-points-validation
  "Validates the individual points of the ring."
  [{:keys [points]}]
  (mapcat (fn [[i point]]
            (when-let [errors (v/validate point)]
              (map (partial msg/ring-point-invalid i) errors)))
          (map-indexed vector points)))

(defn- ring-closed-validation
  "Validates the ring is closed (last point = first point)"
  [{:keys [points]}]
  (when-not (= (first points) (last points))
    [(msg/ring-not-closed)]))

(defn- points->rounded-point-map
  "Combines together points that round to the same value. Takes a sequence of points and returns a
  map of rounded points to list of index, point pairs."
  [points]
  (reduce (fn [m [i point]]
            (let [rounded (p/round-point 8 point)]
              (update-in m [rounded] conj [i point])))
          {}
          (map-indexed vector points)))

(defn- ring-duplicate-point-validation
  "Validates that the ring does not contain any duplicate or very close together points."
  [{:keys [points]}]

  ;; Create a map of the rounded points to list of points that round that same value. If any of the
  ;; rounded points has more than other point in the list then they are duplicates.
  (let [rounded-point-map (points->rounded-point-map (drop-last points))
        duplicate-point-lists (->> rounded-point-map
                                   vals
                                   (filter #(> (count %) 1))
                                   ;; reversing lists of duplicate points to put points in indexed order
                                   ;; for more pleasing messages.
                                   (map reverse))]
    (map msg/ring-duplicate-points duplicate-point-lists)))

(defn- ring-consecutive-antipodal-points-validation
  "Validates that the ring does not have any consecutive antipodal points"
  [{:keys [points]}]

  (let [indexed-points (map-indexed vector points)
        indexed-point-pairs (partition 2 1 indexed-points)
        antipodal-indexed-point-pairs (filter (fn [[[_ p1] [_ p2]]]
                                                (p/antipodal? p1 p2))
                                              indexed-point-pairs)]
    (map (partial apply msg/ring-consecutive-antipodal-points)
         antipodal-indexed-point-pairs)))

(defn- ring-self-intersection-validation
  "Validates that the ring does not intersect itself"
  [ring]
  (when-let [intersections (seq (rr/self-intersections ring))]
    [(msg/ring-self-intersections intersections)]))

(defn- ring-pole-validation
  "Validates that the ring does not contain both poles"
  [ring]
  (let [ring (gr/ring->pole-containment ring)]
    (when (and (:contains-south-pole ring) (:contains-north-pole ring))
      [(msg/ring-contains-both-poles)])))

(extend-protocol v/SpatialValidation
  cmr.spatial.geodetic_ring.GeodeticRing
  (validate
    [ring]
    ;; Certain validations can only be run if earlier validations passed. Validations are grouped
    ;; here so that subsequent validations won't run if earlier validations fail.

    (or (seq (ring-points-validation ring))
        ;; basic ring validation
        (or (seq (concat (ring-closed-validation ring)
                         (ring-duplicate-point-validation ring)
                         (ring-consecutive-antipodal-points-validation ring)))
            ;; Advanced ring validation
            (let [ring (assoc ring :arcs (gr/ring->arcs ring))]
              (or (seq (ring-self-intersection-validation ring))
                  (seq (ring-pole-validation ring))))))))