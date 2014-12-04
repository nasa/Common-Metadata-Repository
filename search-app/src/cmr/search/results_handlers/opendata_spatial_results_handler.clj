(ns cmr.search.results-handlers.opendata-spatial-results-handler
  "A helper for converting spatial shapes into opendata results"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.geodetic-ring :as gr]
            [cmr.spatial.cartesian-ring :as cr]
            [cmr.spatial.line-string :as l]
            [clojure.string :as str]))

(def coordinate-syste->srs-name
  {:cartesian "EPSG:9825" ; Psuedo Plate Carree
   :geodetic "EPSG:4326" ; WGS-84
   })

(defn- points-map->points-str
  "Converts a map containing :points into the lat lon space separated points string of GML"
  [{:keys [points]}]
  (str/join " " (mapcat #(vector (:lat %) (:lon %)) points)))

(defprotocol OpendataSpatialHandler
  (shape->string
    [shape]
    "Converts a spatial shape into the string of ordinates")
  (shape->gml
    [shape]
    "Converts a shape into a GML representation"))

(extend-protocol OpendataSpatialHandler

  cmr.spatial.point.Point
  (shape->string
    [{:keys [lon lat]}]
    (str lat " " lon))

  cmr.spatial.line_string.LineString
  (shape->string
    [line]
    (points-map->points-str line))
  (shape->gml
    [line]
    (let [srs-name (coordinate-syste->srs-name (:coordinate-system line))]
      (x/element :gml:LineString {:srsName srs-name} (shape->string line))))

  cmr.spatial.mbr.Mbr
  (shape->string
    [{:keys [west north east south]}]
    (str/join " " [west south east north]))

  cmr.spatial.geodetic_ring.GeodeticRing
  (shape->string
    [ring]
    (points-map->points-str ring))
  (shape->gml
    [ring]
    (x/element :gml:LinearRing {}
               (x/element :gml:posList {}
                          (shape->string ring))))

  cmr.spatial.cartesian_ring.CartesianRing
  (shape->string
    [ring]
    (points-map->points-str ring))
  (shape->gml
    [ring]
    (x/element :gml:LinearRing {}
               (x/element :gml:posList {}
                          (shape->string ring))))

  cmr.spatial.polygon.Polygon
  (shape->gml
    [{:keys [rings] :as shape}]
    (let [srs-name (coordinate-syste->srs-name (:coordinate-system shape))]
      (x/element :gml:Polygon {:srsName srs-name}
                 (x/element :gml:outerBoundaryIs {}
                            (shape->gml (first rings)))
                 (when-let [holes (rest rings)]
                   (x/element :gml:innerBoundaryIs {}
                              (for [ring holes]
                                (shape->gml ring))))))))

(defn shapes->json
  "Returns the json representation of the given shapes"
  [shapes pretty?]
  (let [xml-fn (if pretty? x/indent-str x/emit-str)
        shapes-by-type (group-by type shapes)
        points (when-let [points (get shapes-by-type cmr.spatial.point.Point)]
                 (shape->string (first points)))
        boxes (when-let [boxes (get shapes-by-type cmr.spatial.mbr.Mbr)]
                (shape->string (first boxes)))
        polygons (when-let [polygons (get shapes-by-type cmr.spatial.polygon.Polygon)]
                   (cx/remove-xml-processing-instructions (xml-fn (shape->gml (first polygons)))))
        lines (when-let [lines (get shapes-by-type cmr.spatial.line_string.LineString)]
                (cx/remove-xml-processing-instructions (xml-fn (shape->gml (first lines)))))]
    (or polygons lines boxes points)))