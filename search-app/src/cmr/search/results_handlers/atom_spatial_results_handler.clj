(ns cmr.search.results-handlers.atom-spatial-results-handler
  "A helper for converting spatial shapes into ATOM results"
  (:require [clojure.data.xml :as xml]
            [clojure.string :as string]
            ;[cmr.spatial.polygon :as poly]
            ;[cmr.spatial.point :as p]
            ;[cmr.spatial.mbr :as m]
            ;[cmr.spatial.geodetic-ring :as gr]
            ;[cmr.spatial.cartesian-ring :as cr]
            ;[cmr.spatial.line-string :as l]
            [cmr.common.util :as util]))

(defn- points-map->points-str
  "Converts a map containing :points into the lat lon space separated points string of atom"
  [{:keys [points]}]
  (string/join " " (mapcat #(vector (util/double->string (:lat %)) (util/double->string (:lon %))) points)))


(defprotocol AtomSpatialHandler
  (shape->string
    [shape]
    "Converts a spatial shape into the string of ordinates")
  (shape->xml-element
    [shape]
    "Converts a spatial shape into the ATOM XML element"))

(extend-protocol AtomSpatialHandler

  cmr.spatial.point.Point
  (shape->string
    [{:keys [lon lat]}]
    (str (util/double->string lat) " " (util/double->string lon)))

  (shape->xml-element
    [point]
    (xml/element :georss:point {} (shape->string point)))


  cmr.spatial.line_string.LineString
  (shape->string
    [line]
    (points-map->points-str line))

  (shape->xml-element
    [line]
    (xml/element :georss:line {} (shape->string line)))


  cmr.spatial.mbr.Mbr
  (shape->string
    [{:keys [west north east south]}]
    (string/join " " (map util/double->string [south west north east])))

  (shape->xml-element
    [mbr]
    (xml/element :georss:box {} (shape->string mbr)))


  cmr.spatial.geodetic_ring.GeodeticRing
  (shape->string
    [ring]
    (points-map->points-str ring))

  (shape->xml-element
    [ring]
    (xml/element :gml:LinearRing {}
               (xml/element :gml:posList {}
                          (shape->string ring))))

  cmr.spatial.cartesian_ring.CartesianRing
  (shape->string
    [ring]
    (points-map->points-str ring))

  (shape->xml-element
    [ring]
    (xml/element :gml:LinearRing {}
               (xml/element :gml:posList {}
                          (shape->string ring))))

  cmr.spatial.polygon.Polygon
  (shape->xml-element
    [{:keys [rings]}]
    (if (= (count rings) 1)
      (xml/element :georss:polygon {} (shape->string (first rings)))
      (let [boundary (first rings)
            holes (rest rings)]
        (xml/element :georss:where {}
                   (xml/element :gml:Polygon {}
                              (xml/element :gml:exterior {} (shape->xml-element boundary))
                              (xml/element :gml:interior {} (map shape->xml-element holes))))))))


(defn- polygon->json
  "Returns the json representation of the given polygon"
  [{:keys [rings]}]
  (if (= (count rings) 1)
    [(shape->string (first rings))]
    (let [boundary (first rings)
          holes (rest rings)]
      (concat [(shape->string boundary)] (map shape->string holes)))))

(defn shapes->json
  "Returns the json representation of the given shapes"
  [shapes]
  (let [shapes-by-type (group-by type shapes)
        points (map shape->string (get shapes-by-type cmr.spatial.point.Point))
        boxes (map shape->string (get shapes-by-type cmr.spatial.mbr.Mbr))
        polygons (map polygon->json (get shapes-by-type cmr.spatial.polygon.Polygon))
        lines (map shape->string (get shapes-by-type cmr.spatial.line_string.LineString))
        spatial {:points points
                 :boxes boxes
                 :polygons polygons
                 :lines lines}]
    (apply dissoc
           spatial
           (for [[k v] spatial :when (empty? v)] k))))


