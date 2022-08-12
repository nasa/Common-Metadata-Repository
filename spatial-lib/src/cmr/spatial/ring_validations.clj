(ns cmr.spatial.ring-validations
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.math :refer :all]
            [cmr.common.util :as util]
            [primitive-math]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.conversion :as c]
            [cmr.spatial.arc :as a]
            [cmr.spatial.derived :as d]
            [cmr.spatial.geodetic-ring :as gr]
            [cmr.spatial.cartesian-ring :as cr]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.validation :as v]
            [cmr.spatial.points-validation-helpers :as pv]
            [cmr.spatial.messages :as msg])
  (:import cmr.spatial.arc.Arc))
(primitive-math/use-primitive-operators)

(defn- ring-closed-validation
  "Validates the ring is closed (last point = first point)"
  [{:keys [points]}]
  (when-not (= (first points) (last points))
    [(msg/ring-not-closed)]))

(defn- ring-self-intersection-validation
  "Validates that the ring does not intersect itself"
  [ring]
  (when-let [intersections (seq (rr/self-intersections ring))]
    [(msg/ring-self-intersections intersections)]))

(defn- ring-pole-validation
  "Validates that a geodetic ring does not contain both poles"
  [ring]
  (let [ring (gr/ring->pole-containment ring)]
    (when (and (:contains-south-pole ring) (:contains-north-pole ring))
      [(msg/ring-contains-both-poles)])))

(defn- ring-geo-point-order-validation
  "Validates that a geodetic rings points are in counter clockwise order"
  [ring]
  (when (= (gr/ring->point-order ring) :clockwise)
    [(msg/ring-points-out-of-order)]))

(defn- ring-point-order-validation
  "Validates that a cartesian rings points are in counter clockwise order"
  [ring]
  (when (not= (cr/ring->winding ring) :counter-clockwise)
    [(msg/ring-points-out-of-order)]))

(extend-protocol v/SpatialValidation
  cmr.spatial.geodetic_ring.GeodeticRing
  (validate
    [ring]
    ;; Certain validations can only be run if earlier validations passed. Validations are grouped
    ;; here so that subsequent validations won't run if earlier validations fail.

    (or (seq (pv/points-in-shape-validation ring))
        ;; basic ring validation
        (or (seq (concat (ring-closed-validation ring)
                         (pv/duplicate-point-validation (update-in ring [:points] drop-last))
                         (pv/consecutive-antipodal-points-validation ring)))
            ;; Advanced ring validation
            (let [ring (assoc ring :arcs (gr/ring->arcs ring))]
              (or (seq (ring-self-intersection-validation ring))
                  (seq (ring-pole-validation ring))
                  (seq (ring-geo-point-order-validation ring)))))))

  cmr.spatial.cartesian_ring.CartesianRing
  (validate
    [ring]
    ;; Certain validations can only be run if earlier validations passed. Validations are grouped
    ;; here so that subsequent validations won't run if earlier validations fail.

    (or (seq (pv/points-in-shape-validation ring))
        ;; basic ring validation
        (or (seq (concat (ring-closed-validation ring)
                         (pv/duplicate-point-validation (update-in ring [:points] drop-last))))
            ;; Advanced ring validation
            (let [ring (assoc ring :line-segments (cr/ring->line-segments ring))]
              (or (seq (ring-self-intersection-validation ring))
                  (seq (ring-point-order-validation ring))))))))