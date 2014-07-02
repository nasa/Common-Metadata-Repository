(ns cmr.spatial.polygon
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.math :refer :all]
            [cmr.common.util :as util]
            [cmr.common.services.errors :as errors]
            [primitive-math]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.conversion :as c]
            [cmr.spatial.ring :as r]
            [cmr.spatial.arc :as a]
            [cmr.spatial.derived :as d]
            [cmr.spatial.validation :as v]
            [cmr.spatial.messages :as msg]))

(primitive-math/use-primitive-operators)

(defrecord Polygon
  [
   rings

   ;; Derived fields
   mbr
   ])

(defn polygon
  "Creates a polygon"
  [rings]
  (->Polygon (vec rings) nil))

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
  (and (r/covers-point? (boundary polygon) point)
       (not-any? #(r/covers-point? % point) (holes polygon))))

(defn covers-br?
  "Returns true if the polygon covers the bounding rectangle."
  [polygon br]
  ;; The outer ring covers br and none of the holes _intersect_ the br
  (and (r/covers-br? (boundary polygon) br)
       (not-any? #(r/intersects-br? % br) (holes polygon))))

(defn intersects-ring?
  "Returns true if the polygon intersects the ring."
  [polygon ring]
  ;; The outer ring intersects the ring and none of the holes _cover_ the ring
  (and (r/intersects-ring? (boundary polygon) ring)
       (not-any? #(r/covers-ring? % ring) (holes polygon))))

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
    (and (r/intersects-ring? boundary1 boundary2)
         (not-any? #(r/covers-ring? % boundary2) holes1)
         (not-any? #(r/covers-ring? % boundary1) holes2))))

(defn intersects-br?
  "Returns true if the polygon the bounding rectangle."
  [polygon br]
  ;; The outer ring intersects the br and none of the holes _cover_ the br
  (and (r/intersects-br? (boundary polygon) br)
       (not-any? #(r/covers-br? % br) (holes polygon))))


(extend-protocol d/DerivedCalculator
  cmr.spatial.polygon.Polygon
  (calculate-derived
    [^Polygon polygon]
    (if (.mbr polygon)
      polygon

      (as-> polygon p
            (update-in p [:rings] (partial mapv d/calculate-derived))
            (assoc p :mbr (-> p :rings first :mbr))))))


(extend-protocol v/SpatialValidation
  cmr.spatial.polygon.Polygon
  (validate
    [{:keys [rings]}]
    (if (> (count rings) 1)
      ;; Hole validation is not supported yet. We only implemented validation of what was possible
      ;; on the search API
      (errors/internal-error! "Validation of polygons with holes is not yet supported")
      (v/validate (first rings)))))


