(ns cmr.search.results-handlers.atom-spatial-results-handler
  "A helper for converting spatial shapes into ATOM results"
  (:require [clojure.data.xml :as x]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.ring :as r]
            [cmr.spatial.line :as l]
            [clojure.string :as str]))

(defprotocol AtomSpatialHandler
  (shape->xml-element
    [shape]
    "Converts a spatial shape into the ATOM XML element"))

(defn points-map->points-str
  "Converts a map containing :points into the lat lon space separated points string of atom"
  [{:keys [points]}]
  (str/join " " (mapcat #(vector (:lat %) (:lon %)) points)))

(defn ring->xml-element
  "Converts a ring _for a polygon with holes_ to an XML element. Polygons with holes do not generate
  this type of element"
  [ring]
  (x/element :gml:LinearRing {}
             (x/element :gml:posList {}
                        (points-map->points-str ring))))

(extend-protocol AtomSpatialHandler

  cmr.spatial.polygon.Polygon
  (shape->xml-element
    [{:keys [rings]}]
    (if (= (count rings) 1)
      (x/element :georss:polygon {} (points-map->points-str (first rings)))
      (let [boundary (first rings)
            holes (rest rings)]
        (x/element :georss:where {}
                   (x/element :gml:Polygon {}
                              (x/element :gml:exterior {} (ring->xml-element boundary))
                              (x/element :gml:interior {} (map ring->xml-element holes)))))))

  cmr.spatial.point.Point
  (shape->xml-element
    [{:keys [lon lat]}]
    (x/element :georss:point {} (str lat " " lon)))

  cmr.spatial.mbr.Mbr
  (shape->xml-element
    [{:keys [west north east south]}]
    (x/element :georss:box {} (str/join " " [south west north east])))

  cmr.spatial.line.Line
  (shape->xml-element
    [line]
    (x/element :georss:line {} (points-map->points-str line))))


