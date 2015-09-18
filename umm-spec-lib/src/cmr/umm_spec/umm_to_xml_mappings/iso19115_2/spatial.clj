(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2.spatial
  "Functions for generating ISO-MENDS XML elements from UMM spatial records."
  (:require [clojure.string :as str]
            [camel-snake-kebab.core :as csk]
            [cmr.spatial.derived :as d]
            [cmr.spatial.encoding.gmd :as gmd]
            [cmr.spatial.line-string :as ls]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.point :as p]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.relations :as r]
            [cmr.spatial.ring-relations :as rr]
            [cmr.umm.collection :as c]
            [cmr.umm.spatial :as umm-s]))

(defn spatial-point
  [umm-point]
  (p/point (:Longitude umm-point) (:Latitude umm-point)))

(defn spatial-lib-shapes
  "Returns new umm-spec spatial GeometryType as a sequence of CMR spatial-lib types."
  [geometry]
  (let [coord-sys (csk/->camelCaseKeyword (:CoordinateSystem geometry))
        make-ring (partial rr/ring coord-sys)]
    (concat
     ;; Points
     (map spatial-point (:Points geometry))
     ;; Polygons
     (for [poly (:GPolygons geometry)]
       (let [exterior-ring  (make-ring (map spatial-point (-> poly :Boundary :Points)))
             interior-rings (for [b (-> poly :ExclusiveZone :Boundaries)]
                              (make-ring (map spatial-point (:Points b))))
             rings (cons exterior-ring interior-rings)]
         (poly/polygon coord-sys rings)))
     ;; Lines
     (for [umm-line (:Lines geometry)]
       (ls/line-string coord-sys (map spatial-point (:Points umm-line))))
     ;; Bounding Rectangles
     (for [rect (:BoundingRectangles geometry)]
       (mbr/mbr (:WestBoundingCoordinate  rect)
                (:NorthBoundingCoordinate rect)
                (:EastBoundingCoordinate  rect)
                (:SouthBoundingCoordinate rect))))))

(defn spatial-elements
  "Returns a sequence of ISO MENDS elements from the given UMM-C collection record."
  [c]
  (let [spatial (:SpatialExtent c)
        coordinate-system :cartesian
        shapes (spatial-lib-shapes (-> spatial :HorizontalSpatialDomain :Geometry))
        _ (println "shapes =" shapes)]
    (->> shapes
         (map (partial umm-s/set-coordinate-system coordinate-system))
         (map d/calculate-derived)
         ;; ISO MENDS interleaves MBRs and actual spatial areas
         (mapcat (juxt r/mbr identity))
         (map gmd/encode))))
