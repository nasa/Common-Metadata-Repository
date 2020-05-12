(ns cmr.spatial.relations
  "This namespace describes functions for determining the relations between various spatial types."
  (:require
    [cmr.spatial.arc :as a]
    [cmr.spatial.cartesian-ring :as cr]
    [cmr.spatial.derived :as d]
    [cmr.spatial.geodetic-ring :as gr]
    [cmr.spatial.line-string :as ls]
    [cmr.spatial.math :refer :all]
    [cmr.spatial.mbr :as m]
    [cmr.spatial.point :as p]
    [cmr.spatial.polygon :as poly]
    [cmr.spatial.ring-relations :as rr])
  (:import
    cmr.spatial.cartesian_ring.CartesianRing
    cmr.spatial.geodetic_ring.GeodeticRing
    cmr.spatial.line_string.LineString
    cmr.spatial.mbr.Mbr
    cmr.spatial.point.Point
    cmr.spatial.polygon.Polygon))

(defprotocol SpatialRelations
  "Defines functions for determining relations between different spatial areas."

  (coordinate-system
    [shape]
    "Returns the coordinate system of a shape if it has one. Points do not occupy
    a set coordinate system.")

  (mbr [shape] "Returns the minimum bounding rectangle of the shape")

  (contains-north-pole? [shape] "Returns true if the shape contains the north pole")
  (contains-south-pole? [shape] "Returns true if the shape contains the south pole")

  (covers-point? [shape point] "Returns true if the shape covers the point. There is no
                               intersects-point? function as they would be equivalent to covers with
                               a point.")
  (covers-br? [shape br] "Returns true if the shape covers the bounding rectangle.")
  (intersects-ring? [shape ring] "Returns true if the shape intersects the ring.")
  (intersects-polygon? [shape polygon] "Returns true if the shape intersects the polygon.")
  (intersects-br? [shape br] "Returns true if the shape intersects the bounding rectangle.")
  (intersects-line-string? [shape line-string] "Returns true if the shape intersects the line string"))

;; Only certain functions are implemented here
(extend-protocol SpatialRelations

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.spatial.point.Point

  (coordinate-system
    [point]
    ;; Points don't have a specific coordinate system
    nil)

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
    (gr/covers-point? ring point))

  (intersects-br?
    [point br]
    (m/geodetic-covers-point? br point))

  (intersects-polygon?
    [point polygon]
    (poly/covers-point? polygon point))

  (intersects-line-string?
    [point line]
    (ls/covers-point? line point))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.spatial.line_string.LineString

  (coordinate-system
    [line]
    (:coordinate-system line))

  (mbr
    [line]
    (:mbr line))

  (contains-north-pole?
    [line]
    (ls/covers-point? line p/north-pole))

  (contains-south-pole?
    [line]
    (ls/covers-point? line p/south-pole))

  (covers-point?
    [line point]
    (ls/covers-point? line point))

  ;; covers-br? not implemented. I'm not sure it would make sense to call that.

  (intersects-ring?
    [line ring]
    (rr/intersects-line-string? ring line))

  (intersects-br?
    [line br]
    (ls/intersects-br? line br))

  (intersects-polygon?
    [line polygon]
    (poly/intersects-line-string? polygon line))

  (intersects-line-string?
    [l1 l2]
    (ls/intersects-line-string? l1 l2))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.spatial.mbr.Mbr

  (coordinate-system
    [_]
    :cartesian)

  (mbr [br] br)

  (contains-north-pole?
    [br]
    (m/covers-lat? br 90.0))

  (contains-south-pole?
    [br]
    (m/covers-lat? br -90.0))

  (covers-point?
    [br point]
    (m/geodetic-covers-point? br point))

  (covers-br?
    [br1 br2]
    (m/covers-mbr? :geodetic br1 br2))

  (intersects-ring?
    [br ring]
    (rr/intersects-br? ring br))

  (intersects-br?
    [br1 br2]
    (m/intersects-br? :geodetic br1 br2))

  (intersects-polygon?
    [br polygon]
    (poly/intersects-br? polygon br))

  (intersects-line-string?
    [br line]
    (ls/intersects-br? line br))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.spatial.geodetic_ring.GeodeticRing

  (coordinate-system
    [_]
    :geodetic)

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
    (gr/covers-point? ring point))

  (covers-br?
    [ring br]
    (rr/covers-br? ring br))

  (intersects-ring?
    [ring1 ring2]
    (rr/intersects-ring? ring1 ring2))

  (intersects-br?
    [ring br]
    (rr/intersects-br? ring br))

  (intersects-polygon?
    [ring polygon]
    (poly/intersects-ring? polygon ring))

  (intersects-line-string?
    [ring line]
    (rr/intersects-line-string? ring line))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.spatial.cartesian_ring.CartesianRing

  (coordinate-system
    [_]
    :cartesian)

  (mbr
    [ring]
    (:mbr ring))

  (contains-north-pole?
    [ring]
    (cr/covers-point? ring p/north-pole))

  (contains-south-pole?
    [ring]
    (cr/covers-point? ring p/south-pole))

  (covers-point?
    [ring point]
    (cr/covers-point? ring point))

  (covers-br?
    [ring br]
    (rr/covers-br? ring br))

  (intersects-ring?
    [ring1 ring2]
    (rr/intersects-ring? ring1 ring2))

  (intersects-br?
    [ring br]
    (rr/intersects-br? ring br))

  (intersects-polygon?
    [ring polygon]
    (poly/intersects-ring? polygon ring))

  (intersects-line-string?
    [ring line]
    (rr/intersects-line-string? ring line))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.spatial.polygon.Polygon

  (coordinate-system
    [polygon]
    (:coordinate-system polygon))

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
    (poly/intersects-polygon? poly1 poly2))

  (intersects-line-string?
    [polygon line]
    (poly/intersects-line-string? polygon line)))

(def shape-type->intersects-fn
  "A map of spatial types to the intersect functions to use."
  {Point covers-point?
   Polygon intersects-polygon?
   Mbr intersects-br?
   LineString intersects-line-string?})

(defn shape->intersects-fn
  "Creates a function for determining if another shape intersects this shape."
  [shape]
  (let [shape (d/calculate-derived shape)
        f (get shape-type->intersects-fn (type shape))]
    (fn [other-shape]
      ;; Shape is the second argument so that the polymorphic protocol dispatch can be used
      ;; on the first argument.
      (f (d/calculate-derived other-shape) shape))))
