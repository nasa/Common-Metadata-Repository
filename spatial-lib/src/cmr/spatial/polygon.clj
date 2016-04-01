(ns cmr.spatial.polygon
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.math :refer :all]
            [cmr.common.util :as util]
            [cmr.common.services.errors :as errors]
            [primitive-math]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.conversion :as c]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.arc :as a]
            [cmr.spatial.derived :as d]
            [cmr.spatial.validation :as v]
            [cmr.spatial.messages :as msg]
            [cmr.common.dev.record-pretty-printer :as record-pretty-printer]
            [clojure.math.combinatorics :as combo]))

(primitive-math/use-primitive-operators)

(defrecord Polygon
  [
   coordinate-system

   rings

   ;; Derived fields
   mbr])

(record-pretty-printer/enable-record-pretty-printing Polygon)

(defn polygon
  "Creates a polygon"
  ([rings]
   ;; Allows the coordinate system to be set at a later time
   (polygon nil rings))
  ([coord-sys rings]
   (->Polygon coord-sys (vec rings) nil)))

(defn boundary
  "Returns the outer boundary"
  [polygon]
  (-> polygon :rings first))

(defn holes
  "Returns the holes of the polygon"
  [polygon]
  (->> polygon :rings (drop 1)))

(defn covers-point?
  "Returns true if the polygon covers the point."
  [polygon point]
  ;; The outer ring covers point and none of the holes _cover_ the point.
  (and (rr/covers-point? (boundary polygon) point)
       (not-any? #(rr/covers-point? % point) (holes polygon))))

(defn covers-br?
  "Returns true if the polygon covers the bounding rectangle."
  [polygon br]
  ;; The outer ring covers br and none of the holes _intersect_ the br
  (and (rr/covers-br? (boundary polygon) br)
       (not-any? #(rr/intersects-br? % br) (holes polygon))))

(defn intersects-ring?
  "Returns true if the polygon intersects the ring."
  [polygon ring]
  ;; The outer ring intersects the ring and none of the holes _cover_ the ring
  (and (rr/intersects-ring? (boundary polygon) ring)
       (not-any? #(rr/covers-ring? % ring) (holes polygon))))

(defn intersects-line-string?
  "Returns true if the polygon intersects the line."
  [polygon line]
  (and (rr/intersects-line-string? (boundary polygon) line)
       (not-any? #(rr/covers-line-string? % line) (holes polygon))))

(defn intersects-polygon?
  "Returns true if the polygon intersects the other polygon"
  [poly1 poly2]
  ;; 1. The outer boundary intersects the other boundary
  ;; 2. None of the holes _cover_ the other polygon
  ;; 3. The polygon isn't inside any of the holes of the other polygon
  (let [boundary1 (boundary poly1)
        boundary2 (boundary poly2)
        holes1 (holes poly1)
        holes2 (holes poly2)]
    (and (rr/intersects-ring? boundary1 boundary2)
         (not-any? #(rr/covers-ring? % boundary2) holes1)
         (not-any? #(rr/covers-ring? % boundary1) holes2))))

(defn intersects-br?
  "Returns true if the polygon the bounding rectangle."
  [polygon br]
  ;; The outer ring intersects the br and none of the holes _cover_ the br
  (and (rr/intersects-br? (boundary polygon) br)
       (not-any? #(rr/covers-br? % br) (holes polygon))))

(extend-protocol d/DerivedCalculator
  cmr.spatial.polygon.Polygon
  (calculate-derived
    [^Polygon polygon]
    (if (.mbr polygon)
      polygon

      (as-> polygon p
            (update p :rings #(mapv d/calculate-derived %))
            (assoc p :mbr (-> p :rings first :mbr))))))

(defn- holes-inside-boundary-validation
  "Validates that all of the holes are completely covered by the boundary of the polygon."
  [polygon]
  (let [boundary (boundary polygon)
        holes (holes polygon)]
    (for [[i hole] (map-indexed vector holes)
          :when (not (rr/covers-ring? boundary hole))]
      (msg/hole-not-covered-by-boundary i))))

(defn- holes-do-not-intersect-validation
  "Validates that holes within a polygon do not intersect"
  [polygon]
  (for [[[hole-index1 hole1] [hole-index2 hole2]]
        (combo/combinations (map-indexed vector (holes polygon)) 2)
        :when (rr/intersects-ring? hole1 hole2)]
    (msg/hole-intersects-hole hole-index1 hole-index2)))

(extend-protocol v/SpatialValidation
  cmr.spatial.polygon.Polygon
  (validate
    [polygon]
    (or (seq (mapcat v/validate (:rings polygon)))
        (let [polygon (d/calculate-derived polygon)]
          (seq (concat (holes-inside-boundary-validation polygon)
                       (holes-do-not-intersect-validation polygon)))))))


