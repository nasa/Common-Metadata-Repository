(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2.spatial
  "Functions for generating ISO19115-2 XML elements from UMM spatial records."
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as string]
   [cmr.common.util :as util]
   [cmr.spatial.derived :as d]
   [cmr.spatial.encoding.gmd :as gmd]
   [cmr.spatial.line-string :as ls]
   [cmr.spatial.mbr :as mbr]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.relations :as r]
   [cmr.spatial.ring-relations :as rr]
   [cmr.umm-spec.migration.spatial-extent-migration :as sp-ext-migration]
   [cmr.umm-spec.spatial-conversion :as spatial-conversion]
   [cmr.umm.umm-spatial :as umm-s]))

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

(defn- remove-whitespace
  [value]
  (when value
    (-> (clojure.string/split value #"\s")
        clojure.string/join)))

(defn- spatial-representation-info
  "Returns the spatialRepresentationInfo ISO XML element for a given UMM-C collection record."
  [indexed-resolution]
  (let [[id resolution] indexed-resolution
        dimensions (as-> (select-keys resolution [:XDimension :YDimension]) values
                         (vals values)
                         (remove nil? values))
        unit (get resolution :Unit)]
    (for [dimension dimensions
          :when dimension]
      [:gmd:axisDimensionProperties {:xlink:href (str "horizontalresolutionandcoordinatesystem_geographiccoordinatesystems" id)}
       [:gmd:MD_Dimension
        [:gmd:dimensionName
         [:gmd:MD_DimensionNameTypeCode {:codeList "https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#MD_DimensionNameTypeCode"
                                         :codeListValue "column"}
          "column"]]
        ;; This is needed to pass validation.
        [:gmd:dimensionSize {:gco:nilReason "inapplicable"}]
        [:gmd:resolution
         [:gco:Measure {:uom (remove-whitespace unit)}
          dimension]]]])))

(defn generate-spatial-representation-infos
  "Returns spatialRepresentationInfo from HorizontalDataResolution values."
  [c]
  (when-let [group-horizontal-data-resolutions (get-in c [:SpatialExtent :HorizontalSpatialDomain
                                                          :ResolutionAndCoordinateSystem :HorizontalDataResolution])]
    (let [horizontal-data-resolutions (sp-ext-migration/degroup-resolutions group-horizontal-data-resolutions)]
      [:gmd:spatialRepresentationInfo
       [:gmd:MD_GridSpatialRepresentation
        [:gmd:numberOfDimensions
         [:gco:Integer
          (count (mapcat #(vals (select-keys % [:XDimension :YDimension])) horizontal-data-resolutions))]]
        (mapcat spatial-representation-info
                (map-indexed vector horizontal-data-resolutions))
        [:gmd:cellGeometry {:gco:nilReason "inapplicable"}]
        [:gmd:transformationParameterAvailability {:gco:nilReason "inapplicable"}]]])))

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
                            (util/double->string SwathWidth)
                            (util/double->string Period)
                            (util/double->string InclinationAngle)
                            (util/double->string NumberOfOrbits))]
    ;; StartCircularLatitude is the only optional element
    (if StartCircularLatitude
      (str main-string " StartCircularLatitude: " (util/double->string StartCircularLatitude))
      main-string)))

(defn generate-orbit-parameters
  "Returns a geographic element for the orbit parameters"
  [c]
  (when-let [orbit-parameters (-> c :SpatialExtent :OrbitParameters)]
    [:gmd:geographicElement
     [:gmd:EX_GeographicDescription {:id "OrbitParameters"}
      [:gmd:geographicIdentifier
       [:gmd:MD_Identifier
        [:gmd:code
         [:gco:CharacterString (orbit-parameters->encoded-str orbit-parameters)]]
        [:gmd:codeSpace
         [:gco:CharacterString "gov.nasa.esdis.umm.orbitparameters"]]
        [:gmd:description
         [:gco:CharacterString "OrbitParameters"]]]]]]))

(defn spatial-extent-elements
  "Returns a sequence of ISO MENDS elements from the given UMM-C collection record."
  [c]
  (let [spatial (:SpatialExtent c)
        coordinate-system :cartesian
        shapes (spatial-lib-shapes (-> spatial :HorizontalSpatialDomain :Geometry))]
    (->> shapes
         (map (partial umm-s/set-coordinate-system coordinate-system))
         (map gmd/encode))))

(defn- resolution-and-coordinates-map->string
  "Converts map to parsable string for ISO format."
  [rcmap]
  (when-let [map-keys (keys rcmap)]
   (let [key-val-strings (map #(str (name %)  ": " (get rcmap %)) map-keys)]
     (clojure.string/join " " key-val-strings))))

(defn generate-resolution-and-coordinate-system-horizontal-data-resolutions
  "Returns a sequence of ISO MENDS elements from the given UMM-C collection record."
  [c]
  (when-let [group-horizontal-data-resolutions (get-in c [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem
                                                          :HorizontalDataResolution])]
    (let [horizontal-data-resolutions (sp-ext-migration/degroup-resolutions group-horizontal-data-resolutions)]
      (for [[id indexed-horizontal-data-resolution] (map-indexed vector horizontal-data-resolutions)]
        [:gmd:geographicElement
         [:gmd:EX_GeographicDescription {:id (str "horizontalresolutionandcoordinatesystem_horizontaldataresolutions" id)}
          [:gmd:geographicIdentifier
           [:gmd:MD_Identifier
            [:gmd:code
             [:gco:CharacterString (resolution-and-coordinates-map->string indexed-horizontal-data-resolution)]]
            [:gmd:codeSpace
              [:gco:CharacterString "gov.nasa.esdis.umm.horizontalresolutionandcoordinatesystem_GeographicCoordinateSystems"]]
            [:gmd:description
             [:gco:CharacterString "HorizontalResolutionAndCoordinateSystem_GeographicCoordinateSystems"]]]]]]))))

(defn generate-resolution-and-coordinate-system-local-coords
  "Returns a sequence of ISO MENDS elements from the given UMM-C collection record."
  [c]
  (when-let [local-coords (get-in c [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem :LocalCoordinateSystem])]
    [:gmd:geographicElement
     [:gmd:EX_GeographicDescription {:id "horizontalresolutionandcoordinatesystem_localcoordinatesystem"}
      [:gmd:geographicIdentifier
       [:gmd:MD_Identifier
        [:gmd:code
         [:gco:CharacterString (resolution-and-coordinates-map->string local-coords)]]
        [:gmd:codeSpace
          [:gco:CharacterString "gov.nasa.esdis.umm.horizontalresolutionandcoordinatesystem_localcoordinatesystem"]]
        [:gmd:description
         [:gco:CharacterString "HorizontalResolutionAndCoordinateSystem_LocalCoordinateSystem"]]]]]]))

(defn generate-resolution-and-coordinate-system-geodetic-model
  "Returns a sequence of ISO MENDS elements from the given UMM-C collection record."
  [c]
  (when-let [geodetic-model (get-in c [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem :GeodeticModel])]
    [:gmd:geographicElement
     [:gmd:EX_GeographicDescription {:id "horizontalresolutionandcoordinatesystem_geodeticmodel"}
      [:gmd:geographicIdentifier
       [:gmd:MD_Identifier
        [:gmd:code
         [:gco:CharacterString (resolution-and-coordinates-map->string geodetic-model)]]
        [:gmd:codeSpace
          [:gco:CharacterString "gov.nasa.esdis.umm.horizontalresolutionandcoordinatesystem_geodeticmodel"]]
        [:gmd:description
         [:gco:CharacterString "HorizontalResolutionAndCoordinateSystem_GeodeticModel"]]]]]]))

(defn generate-resolution-and-coordinate-system-description
  "Returns a sequence of ISO MENDS elements from the given UMM-C collection record."
  [c]
  (when-let [description (get-in c [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem :Description])]
    [:gmd:geographicElement
     [:gmd:EX_GeographicDescription {:id "horizontalresolutionandcoordinatesystem"}
      [:gmd:geographicIdentifier
       [:gmd:MD_Identifier
        [:gmd:code
         [:gco:CharacterString (str "Description: " description)]]
        [:gmd:codeSpace
          [:gco:CharacterString "gov.nasa.esdis.umm.horizontalresolutionandcoordinatesystem"]]
        [:gmd:description
         [:gco:CharacterString "HorizontalResolutionAndCoordinateSystem"]]]]]]))

(defn generate-zone-identifier
  "Returns a geographic element for the zone identifier"
  [c]
  (when-let [zone-identifier (get-in c [:SpatialExtent :HorizontalSpatialDomain :ZoneIdentifier])]
    [:gmd:geographicElement
     [:gmd:EX_GeographicDescription {:id "ZoneIdentifier"}
      [:gmd:geographicIdentifier
       [:gmd:MD_Identifier
        [:gmd:code
         [:gco:CharacterString zone-identifier]]
        [:gmd:codeSpace
          [:gco:CharacterString "gov.nasa.esdis.umm.zoneidentifier"]]
        [:gmd:description
         [:gco:CharacterString "ZoneIdentifier"]]]]]]))

(defn- vertical-domain->encoded-str
  "Encodes the vertical domain values as a string."
  [{:keys [Type Value]}]
  (format "Type: %s Value: %s" Type Value))

(defn generate-vertical-domain
  "Returns a geographic element for the vertical domain"
  [c]
  (when-let [vertical-domains (spatial-conversion/drop-invalid-vertical-spatial-domains
                               (get-in c [:SpatialExtent :VerticalSpatialDomains]))]
    (for [x (range (count vertical-domains))
           :let [vertical-domain (nth vertical-domains x)]]
      [:gmd:geographicElement
       [:gmd:EX_GeographicDescription {:id (str "VerticalSpatialDomain" x)}
        [:gmd:geographicIdentifier
         [:gmd:MD_Identifier
          [:gmd:code
           [:gco:CharacterString (vertical-domain->encoded-str vertical-domain)]]
          [:gmd:codeSpace
           [:gco:CharacterString "gov.nasa.esdis.umm.verticalspatialdomain"]]
          [:gmd:description
           [:gco:CharacterString "VerticalSpatialDomain"]]]]]])))
