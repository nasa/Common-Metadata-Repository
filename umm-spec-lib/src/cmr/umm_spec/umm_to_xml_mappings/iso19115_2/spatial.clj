(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2.spatial
  "Functions for generating ISO19115-2 XML elements from UMM spatial records."
  (:require [camel-snake-kebab.core :as csk]
            [cmr.spatial.derived :as d]
            [cmr.spatial.encoding.gmd :as gmd]
            [cmr.spatial.line-string :as ls]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.point :as p]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.relations :as r]
            [cmr.spatial.ring-relations :as rr]
            [cmr.umm.spatial :as umm-s]
            [cmr.common.util :as u]))

(defn spatial-point
  [umm-point]
  (p/point (:Longitude umm-point) (:Latitude umm-point)))

(defn spatial-lib-shapes
  "Returns new umm-spec spatial GeometryType as a sequence of CMR spatial-lib types."
  [geometry]
  (let [coord-sys (some-> geometry :CoordinateSystem csk/->camelCaseKeyword)
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

(defn coordinate-system-element
  "Returns the spatial coordinate system ISO XML element for a given UMM-C collection record."
  [c]
  [:gmd:referenceSystemInfo
   [:gmd:MD_ReferenceSystem
    [:gmd:referenceSystemIdentifier
     [:gmd:RS_Identifier
      [:gmd:code
       [:gco:CharacterString
        (-> c :SpatialExtent :HorizontalSpatialDomain :Geometry :CoordinateSystem)]]]]]])

(defn- orbit-parameters->encoded-str
  "Encodes the orbit parameters as a string."
  [{:keys [SwathWidth Period InclinationAngle NumberOfOrbits StartCircularLatitude]}]
  (let [main-string (format "SwathWidth: %s Period: %s InclinationAngle: %s NumberOfOrbits: %s"
                            (u/double->string SwathWidth)
                            (u/double->string Period)
                            (u/double->string InclinationAngle)
                            (u/double->string NumberOfOrbits))]
    ;; StartCircularLatitude is the only optional element
    (if StartCircularLatitude
      (str main-string " StartCircularLatitude: " (u/double->string StartCircularLatitude))
      main-string)))

(defn generate-orbit-parameters
  "Returns a geographic element for the orbit parameters"
  [c]
  (when-let [orbit-parameters (-> c :SpatialExtent :OrbitParameters)]
    [:gmd:geographicElement
     [:gmd:EX_GeographicDescription
      [:gmd:geographicIdentifier
       [:gmd:MD_Identifier
        [:gmd:code
         [:gco:CharacterString "Orbit"]]
        [:gmd:description
         [:gco:CharacterString (orbit-parameters->encoded-str orbit-parameters)]]]]]]))

(defn spatial-extent-elements
  "Returns a sequence of ISO MENDS elements from the given UMM-C collection record."
  [c]
  (let [spatial (:SpatialExtent c)
        coordinate-system :cartesian
        shapes (spatial-lib-shapes (-> spatial :HorizontalSpatialDomain :Geometry))]
    (->> shapes
         (map (partial umm-s/set-coordinate-system coordinate-system))
         (map d/calculate-derived)
         ;; ISO MENDS interleaves MBRs and actual spatial areas
         (mapcat (juxt r/mbr identity))
         (map gmd/encode))))
