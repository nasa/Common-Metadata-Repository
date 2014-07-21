(ns cmr.search.results-handlers.json-spatial-results-handler
  "A helper for converting spatial shapes into JSON results"
  (:require [clojure.string :as str]
            [cmr.search.results-handlers.atom-spatial-results-handler :as atom-spatial]))

(defprotocol JsonSpatialHandler
  (shape->string
    [shape]
    "Converts a spatial shape into the string of ordinates"))

(defn ring->string
  "Converts a ring to a string."
  [ring]
  (atom-spatial/points-map->points-str ring))

(extend-protocol JsonSpatialHandler

  cmr.spatial.polygon.Polygon
  (shape->string
    [{:keys [rings]}]
    (if (= (count rings) 1)
      [(atom-spatial/points-map->points-str (first rings))]
      (let [boundary (first rings)
            holes (rest rings)]
        (concat [(ring->string boundary)] (map ring->string holes)))))

  cmr.spatial.point.Point
  (shape->string
    [{:keys [lon lat]}]
    (str lat " " lon))

  cmr.spatial.mbr.Mbr
  (shape->string
    [{:keys [west north east south]}]
    (str/join " " [south west north east]))

  cmr.spatial.line.Line
  (shape->string
    [line]
    (atom-spatial/points-map->points-str line)))

(defn shapes->json
  "Returns the json representation of the given shapes"
  [shapes]
  (let [shapes-by-type (group-by type shapes)
        points (map shape->string (get shapes-by-type cmr.spatial.point.Point))
        boxes (map shape->string (get shapes-by-type cmr.spatial.mbr.Mbr))
        polygons (map shape->string (get shapes-by-type cmr.spatial.polygon.Polygon))
        lines (map shape->string (get shapes-by-type cmr.spatial.line.Line))
        spatial {:points points
                 :boxes boxes
                 :polygons polygons
                 :lines lines}]
    (apply dissoc
           spatial
           (for [[k v] spatial :when (empty? v)] k))))
