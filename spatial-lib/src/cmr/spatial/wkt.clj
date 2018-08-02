(ns cmr.spatial.wkt
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

(defrecord Wkt
  [
   coordinate-system

   rings

   ;; Derived fields
   mbr])

(record-pretty-printer/enable-record-pretty-printing Wkt)

(defn wkt
  "Creates a wkt"
  ([rings]
   ;; Allows the coordinate system to be set at a later time
   (wkt nil rings))
  ([coord-sys rings]
   (->Wkt coord-sys (vec rings) nil)))

(defn boundary
  "Returns the outer boundary"
  [wkt]
  (-> wkt :rings first))

(defn holes
  "Returns the holes of the wkt"
  [wkt]
  (->> wkt :rings (drop 1)))

(defn covers-point?
  "Returns true if the wkt covers the point."
  [wkt point]
  ;; The outer ring covers point and none of the holes _cover_ the point.
  (and (rr/covers-point? (boundary wkt) point)
       (not-any? #(rr/covers-point? % point) (holes wkt))))

(defn covers-br?
  "Returns true if the wkt covers the bounding rectangle."
  [wkt br]
  ;; The outer ring covers br and none of the holes _intersect_ the br
  (and (rr/covers-br? (boundary wkt) br)
       (not-any? #(rr/intersects-br? % br) (holes wkt))))

(defn intersects-ring?
  "Returns true if the wkt intersects the ring."
  [wkt ring]
  ;; The outer ring intersects the ring and none of the holes _cover_ the ring
  (and (rr/intersects-ring? (boundary wkt) ring)
       (not-any? #(rr/covers-ring? % ring) (holes wkt))))

(defn intersects-line-string?
  "Returns true if the wkt intersects the line."
  [wkt line]
  (and (rr/intersects-line-string? (boundary wkt) line)
       (not-any? #(rr/covers-line-string? % line) (holes wkt))))

(defn intersects-wkt?
  "Returns true if the wkt intersects the other wkt"
  [wkt1 wkt2]
  ;; 1. The outer boundary intersects the other boundary
  ;; 2. None of the holes _cover_ the other wkt
  ;; 3. The wkt isn't inside any of the holes of the other wkt
  (let [boundary1 (boundary wkt1)
        boundary2 (boundary wkt2)
        holes1 (holes wkt1)
        holes2 (holes wkt2)]
    (and (rr/intersects-ring? boundary1 boundary2)
         (not-any? #(rr/covers-ring? % boundary2) holes1)
         (not-any? #(rr/covers-ring? % boundary1) holes2))))

(defn intersects-br?
  "Returns true if the wkt the bounding rectangle."
  [wkt br]
  ;; The outer ring intersects the br and none of the holes _cover_ the br
  (and (rr/intersects-br? (boundary wkt) br)
       (not-any? #(rr/covers-br? % br) (holes wkt))))

(extend-protocol d/DerivedCalculator
  cmr.spatial.wkt.Wkt
  (calculate-derived
    [^Wkt wkt]
    (if (.mbr wkt)
      wkt

      (as-> wkt p
            (update p :rings #(mapv d/calculate-derived %))
            (assoc p :mbr (-> p :rings first :mbr))))))

(defn- holes-inside-boundary-validation
  "Validates that all of the holes are completely covered by the boundary of the wkt."
  [wkt]
  (let [boundary (boundary wkt)
        holes (holes wkt)]
    (for [[i hole] (map-indexed vector holes)
          :when (not (rr/covers-ring? boundary hole))]
      (msg/hole-not-covered-by-boundary i))))

(defn- holes-do-not-intersect-validation
  "Validates that holes within a wkt do not intersect"
  [wkt]
  (for [[[hole-index1 hole1] [hole-index2 hole2]]
        (combo/combinations (map-indexed vector (holes wkt)) 2)
        :when (rr/intersects-ring? hole1 hole2)]
    (msg/hole-intersects-hole hole-index1 hole-index2)))

(extend-protocol v/SpatialValidation
  cmr.spatial.wkt.Wkt
  (validate
    [wkt]
    (or (seq (mapcat v/validate (:rings wkt)))
        (let [wkt (d/calculate-derived wkt)]
          (seq (concat (holes-inside-boundary-validation wkt)
                       (holes-do-not-intersect-validation wkt)))))))


