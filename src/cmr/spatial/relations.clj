(ns cmr.spatial.relations
  "This namespace describes functions for determining the relations between various spatial types."
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.ring :as r]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.derived :as d]
            [cmr.spatial.math :refer :all])
  (:import cmr.spatial.point.Point
           cmr.spatial.ring.Ring
           cmr.spatial.mbr.Mbr
           cmr.spatial.polygon.Polygon))

(defprotocol SpatialRelations
  "Defines functions for determining relations between different spatial areas."

  (mbr [shape] "Returns the minimum bounding rectangle of the shape")

  (contains-north-pole? [shape] "Returns true if the shape contains the north pole")
  (contains-south-pole? [shape] "Returns true if the shape contains the south pole")

  (covers-point? [shape point] "Returns true if the shape covers the point. There is no
                               intersects-point? function as they would be equivalent to covers with
                               a point.")
  (covers-br? [shape br] "Returns true if the shape covers the bounding rectangle.")
  (intersects-ring? [shape ring] "Returns true if the shape intersects the ring.")
  (intersects-polygon? [shape polygon] "Returns true if the shape intersects the polygon.")
  (intersects-br? [shape br] "Returns true if the shape intersects the bounding rectangle."))

;; Only certain functions are implemented here
(extend-protocol SpatialRelations

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.spatial.point.Point

  (mbr
    [point]
    (m/point->mbr point))

  (contains-north-pole?
    [point]
    (p/is-north-pole? point))

  (contains-south-pole?
    [point]
    (p/is-south-pole? point))

  (covers-point?
    [p1 p2]
    (approx= p1 p2))

  ;; covers-br? not implemented. I'm not sure it would make sense to call that.
  ;; Theoretically we could check if the br is the corner points are all approx= to point

  (intersects-ring?
    [point ring]
    (r/covers-point? ring point))

  (intersects-br?
    [point br]
    (m/covers-point? br point))

  (intersects-polygon?
    [point polygon]
    (poly/covers-point? polygon point))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.spatial.mbr.Mbr

  (mbr [br] br)

  (contains-north-pole?
    [br]
    (m/covers-lat? br 90.0))

  (contains-south-pole?
    [br]
    (m/covers-lat? br -90.0))

  (covers-point?
    [br point]
    (m/covers-point? br point))

  (covers-br?
    [br1 br2]
    (m/covers-mbr? br1 br2))

  (intersects-ring?
    [br ring]
    (r/intersects-br? ring br))

  (intersects-br?
    [br1 br2]
    (m/intersects-br? br1 br2))

  (intersects-polygon?
    [br polygon]
    (poly/intersects-br? polygon br))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.spatial.ring.Ring

  (mbr
    [ring]
    (:mbr ring))

  (contains-north-pole?
    [ring]
    (:contains-north-pole ring))

  (contains-south-pole?
    [ring]
    (:contains-south-pole ring))

  (covers-point?
    [ring point]
    (r/covers-point? ring point))

  (covers-br?
    [ring br]
    (r/covers-br? ring br))

  (intersects-ring?
    [ring1 ring2]
    (r/intersects-ring? ring1 ring2))

  (intersects-br?
    [ring br]
    (r/intersects-br? ring br))

  (intersects-polygon?
    [ring polygon]
    (poly/intersects-ring? polygon ring))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.spatial.polygon.Polygon

  (mbr
    [polygon]
    (-> polygon
        :rings
        first
        :mbr))

  (contains-north-pole?
    [polygon]
    (poly/covers-point? polygon p/north-pole))

  (contains-south-pole?
    [polygon]
    (poly/covers-point? polygon p/south-pole))

  (covers-point?
    [polygon point]
    (poly/covers-point? polygon point))

  (covers-br?
    [polygon br]
    (poly/covers-br? polygon br))

  (intersects-ring?
    [polygon ring]
    (poly/intersects-ring? polygon ring))

  (intersects-br?
    [polygon br]
    (poly/intersects-br? polygon br))

  (intersects-polygon?
    [poly1 poly2]
    (poly/intersects-polygon? poly1 poly2)))

(def shape-type->intersects-fn
  "A map of spatial types to the intersect functions to use."
  {Point covers-point?
   Polygon intersects-polygon?
   Mbr intersects-br?})

(defn shape->intersects-fn
  "Creates a function for determining if another shape intersects this shape."
  [shape]
  (let [shape (d/calculate-derived shape)
        f (get shape-type->intersects-fn (type shape))]
    (fn [other-shape]
      ;; Shape is the second argument so that the polymorphic protocol dispatch can be used
      ;; on the first argument.
      (f (d/calculate-derived other-shape) shape))))

