(ns cmr.search.results-handlers.stac-spatial-results-handler
  "A helper for converting spatial shapes into stac results"
  (:require
   [cmr.spatial.cartesian-ring :as cr]
   [cmr.spatial.derived :as d]
   [cmr.spatial.geodetic-ring :as gr]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]))

(defprotocol stacSpatialHandler
  "Converts a spatial shape into GeoJSON structures"
  (shape->coordinates
    [shape]
    "Returns GeoJSON coordinates for the given spatial shape")

  (shape->mbr
    [shape]
    "Returns the mbr for the given spatial shape"))

(defn- points-map->coordinates
  "Converts a map containing :points into a list of coordinates for stac"
  [{:keys [points]}]
  (map shape->coordinates points))

(extend-protocol stacSpatialHandler

  cmr.spatial.point.Point
  (shape->coordinates
   [{:keys [lon lat]}]
   [lon lat])

  (shape->mbr
    [{:keys [lon lat]}]
    (m/mbr lon lat lon lat))


  cmr.spatial.mbr.Mbr
  (shape->coordinates
   [{:keys [west north east south]}]
   [[
     [west, south]
     [east, south]
     [east, north]
     [west, north]
     [west, south]
     ]])

  (shape->mbr
    [mbr]
    mbr)


  cmr.spatial.line_string.LineString
  (shape->coordinates
   [line]
   (points-map->coordinates line))

  (shape->mbr
    [line]
    (.mbr (d/calculate-derived line)))


  cmr.spatial.geodetic_ring.GeodeticRing
  (shape->coordinates
   [ring]
   (points-map->coordinates ring))


  cmr.spatial.cartesian_ring.CartesianRing
  (shape->coordinates
   [ring]
   (points-map->coordinates ring))


  cmr.spatial.polygon.Polygon
  (shape->coordinates
   [{:keys [rings]}]
   (map shape->coordinates rings))

  (shape->mbr
    [polygon]
    (.mbr (d/calculate-derived polygon))))

(defn shapes->stac-geometry
  "Returns the STAC json representation of the given shapes"
  [shapes]
  (let [shapes-by-type (group-by type shapes)
        points (get shapes-by-type cmr.spatial.point.Point)
        lines (get shapes-by-type cmr.spatial.line_string.LineString)
        boxes (get shapes-by-type cmr.spatial.mbr.Mbr)
        polygons (get shapes-by-type cmr.spatial.polygon.Polygon)]

    (cond
      (seq points)
      (let [coordinates (map shape->coordinates points)]
        (if (> (count points) 1)
          {:type "MultiPoint"
           :coordinates coordinates}
          {:type "Point"
           :coordinates (first coordinates)}))

      (seq lines)
      (let [coordinates (map shape->coordinates lines)]
        (if (> (count lines) 1)
          {:type "MultiLineString"
           :coordinates coordinates}
          {:type "LineString"
           :coordinates (first coordinates)}))

      (seq boxes)
      (let [coordinates (map shape->coordinates boxes)]
        (if (> (count boxes) 1)
          {:type "MultiPolygon"
           :coordinates coordinates}
          {:type "Polygon"
           :coordinates (first coordinates)}))

      (seq polygons)
      (let [coordinates (map shape->coordinates polygons)]
        (if (> (count polygons) 1)
          {:type "MultiPolygon"
           :coordinates coordinates}
          {:type "Polygon"
           :coordinates (first coordinates)}))

      :else nil)))

(defn- mbr->bbox
  "Returns the STAC bbox for the given spatial mbr"
  [{:keys [west north east south]}]
  [west south east north])

(defn shapes->stac-bbox
  "Returns the STAC bbox representation of the given shapes"
  [shapes]
  (when (seq shapes)
    (let [mbrs (map shape->mbr shapes)
          mbr (if (> (count shapes) 1)
                (reduce m/union mbrs)
                (first mbrs))]
      (mbr->bbox mbr))))
