(ns cmr.umm.dif10.collection.spatial
  "Contains functions for convert spatial to and parsing from DIF10 XML."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.line-string :as l]
            [cmr.spatial.polygon :as poly]
            [cmr.umm.umm-spatial :as umm-s]
            [camel-snake-kebab.core :as csk]
            [cmr.common.util :as util]
            [cmr.umm.dif10.collection.two-d-coordinate-system :as two-d]))

(defmulti parse-geometry
  "Parses a geometry element based on the tag of the element."
  (fn [element]
    (:tag element)))

(defmethod parse-geometry :Polygon
  [element]
  (let [outer-ring (parse-geometry (cx/element-at-path element [:Boundary]))
        holes (map parse-geometry (cx/elements-at-path element [:Exclusive_Zone :Boundary]))]
    (poly/polygon (cons outer-ring holes))))

(defmethod parse-geometry :Point
  [element]
  (let [lon (cx/double-at-path element [:Point_Longitude])
        lat (cx/double-at-path element [:Point_Latitude])]
    (p/point lon lat)))

(defmethod parse-geometry :Line
  [element]
  (l/line-string (map parse-geometry (:content element))))

(defmethod parse-geometry :Boundary
  [element]
  (let [points (reverse (map parse-geometry (:content element)))
        points (concat points [(first points)])]
    (umm-s/ring points)))

(defmethod parse-geometry :Bounding_Rectangle
  [element]
  (let [west (cx/double-at-path element [:Westernmost_Longitude])
        east (cx/double-at-path element [:Easternmost_Longitude])
        north (cx/double-at-path element [:Northernmost_Latitude])
        south (cx/double-at-path element [:Southernmost_Latitude])]
    (mbr/mbr west north east south)))

(def geometry-tags
  "The list of geometry tags in the geometry element that are actual spatial area elements"
  #{:Polygon :Point :Line :Boundary :Bounding_Rectangle})

(defn geometry-element->geometries
  "Converts a Geometry element into a sequence of spatial geometry objects"
  [geom-elem]
  (seq (map parse-geometry (filter (comp geometry-tags :tag) (:content geom-elem)))))

(defn- xml-elem->OrbitParameters
  "Returns a UMM OrbitParameters record from a parsed OrbitParameters XML structure"
  [orbit-params]
  (when orbit-params
    (c/map->OrbitParameters
      {:swath-width (cx/double-at-path orbit-params [:Swath_Width])
       :period (cx/double-at-path orbit-params [:Period])
       :inclination-angle (cx/double-at-path orbit-params [:Inclination_Angle])
       :number-of-orbits (cx/double-at-path orbit-params [:Number_Of_Orbits])
       :start-circular-latitude (cx/double-at-path orbit-params [:Start_Circular_Latitude])})))

(defn xml-elem->SpatialCoverage
  "Returns a UMM SpatialCoverage from a parsed Collection XML structure"
  [xml-struct]
  (if-let [spatial-elem (cx/element-at-path xml-struct [:Spatial_Coverage])]
    (let [gsr (csk/->kebab-case-keyword (cx/string-at-path spatial-elem [:Granule_Spatial_Representation]))
          orbit-params (cx/element-at-path spatial-elem [:Orbit_Parameters])]
      (if-let [geom-elem (cx/element-at-path spatial-elem [:Geometry])]
        (c/map->SpatialCoverage
          {:granule-spatial-representation gsr
           :orbit-parameters (xml-elem->OrbitParameters orbit-params)
           :spatial-representation (csk/->kebab-case-keyword (cx/string-at-path geom-elem [:Coordinate_System]))
           :geometries (geometry-element->geometries geom-elem)})
        (c/map->SpatialCoverage
          {:granule-spatial-representation gsr
           :orbit-parameters (xml-elem->OrbitParameters orbit-params)})))))

(defprotocol ShapeToXml
  "Protocol for converting a shape into XML."

  (shape-to-xml
    [shape]
    "Converts the shape into a XML struct element"))

(defn- ring-to-xml
  [ring]
  (x/element :Boundary {}
             (map shape-to-xml
                  ;; Points must be specified in clockwise order and not closed.
                  (-> (:points ring)
                      ;; drop first point since last point will match
                      drop-last
                      ;; counter-clockwise to clockwise
                      reverse))))

(extend-protocol ShapeToXml
  cmr.spatial.point.Point
  (shape-to-xml
    [{:keys [lon lat]}]
    (x/element :Point {}
               (x/element :Point_Longitude {} (util/double->string lon))
               (x/element :Point_Latitude {} (util/double->string lat))))

  cmr.spatial.mbr.Mbr
  (shape-to-xml
    [{:keys [west north east south]}]
    (x/element :Bounding_Rectangle {}
               (x/element :Southernmost_Latitude {} (util/double->string south))
               (x/element :Northernmost_Latitude {} (util/double->string north))
               (x/element :Westernmost_Longitude {} (util/double->string west))
               (x/element :Easternmost_Longitude {} (util/double->string east))))

  cmr.spatial.line_string.LineString
  (shape-to-xml
    [{:keys [points]}]
    (x/element :Line {} (map shape-to-xml points)))

  cmr.spatial.geodetic_ring.GeodeticRing
  (shape-to-xml
    [ring]
    (ring-to-xml ring))

  cmr.spatial.cartesian_ring.CartesianRing
  (shape-to-xml
    [ring]
    (ring-to-xml ring))

  cmr.umm.umm_spatial.GenericRing
  (shape-to-xml
    [ring]
    (ring-to-xml ring))

  cmr.spatial.polygon.Polygon
  (shape-to-xml
    [{:keys [rings]}]
    (let [boundary (first rings)
          holes (seq (rest rings))]
      (x/element :Polygon {}
                 (shape-to-xml boundary)
                 (when holes
                   (x/element :Exclusive_Zone {} (map shape-to-xml holes)))))))

(defn generate-orbit-parameters
  "Generates the OrbitParameters element from orbit-params"
  [orbit-params]
  (when orbit-params
    (let [{:keys [swath-width period inclination-angle number-of-orbits start-circular-latitude]}
          orbit-params]
      (x/element :Orbit_Parameters {}
                 (x/element :Swath_Width {} swath-width)
                 (x/element :Period {} period)
                 (x/element :Inclination_Angle {} inclination-angle)
                 (x/element :Number_Of_Orbits {} number-of-orbits)
                 (when start-circular-latitude
                   (x/element :Start_Circular_Latitude {} start-circular-latitude))))))

(defn generate-spatial-coverage
  "Generates the DIF10 Spatial_Coverage element from UMM spatial coverage and two d coordinate systems"
  [coll]
  (let [{:keys [spatial-coverage two-d-coordinate-systems]} coll]
    (if spatial-coverage
      (let [{:keys [granule-spatial-representation
                    spatial-representation
                    geometries
                    orbit-parameters]} spatial-coverage
            gsr (csk/->SCREAMING_SNAKE_CASE_STRING granule-spatial-representation)
            sr (some-> spatial-representation csk/->SCREAMING_SNAKE_CASE_STRING)]
        (if sr
          (x/element :Spatial_Coverage {}
                     (x/element :Granule_Spatial_Representation {} gsr)
                     (x/element :Geometry {}
                                (x/element :Coordinate_System {} sr)
                                (for [geometry geometries]
                                  (shape-to-xml geometry)))
                     (generate-orbit-parameters orbit-parameters)
                     (two-d/generate-two-ds two-d-coordinate-systems))
          (x/element :Spatial_Coverage {}
                     (x/element :Granule_Spatial_Representation {} gsr)
                     (generate-orbit-parameters orbit-parameters)
                     (two-d/generate-two-ds two-d-coordinate-systems))))
      ;; Added since Spatial_Coverage is a required field in DIF10. CMRIN-79
      (x/element :Spatial_Coverage {}
                 (x/element :Granule_Spatial_Representation {} "CARTESIAN")
                 (two-d/generate-two-ds two-d-coordinate-systems)))))

