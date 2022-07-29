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
        dimensions (as-> (select-keys resolution [:XDimension :MaximumXDimension :MinimumXDimension :YDimension :MaximumYDimension :MinimumYDimension]) values
                         (remove nil? values))
        unit (get resolution :Unit)]
    (for [dimension dimensions
          :when dimension]
      [:gmd:axisDimensionProperties {:xlink:href (str "horizontalresolutionandcoordinatesystem_horizontaldataresolutions" id)}
       [:gmd:MD_Dimension
        [:gmd:dimensionName
         (if (string/includes? (first dimension) "X")
           [:gmd:MD_DimensionNameTypeCode {:codeList "https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#MD_DimensionNameTypeCode"
                                           :codeListValue "column"}
             "column"]
           [:gmd:MD_DimensionNameTypeCode {:codeList "https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#MD_DimensionNameTypeCode"
                                           :codeListValue "row"}
             "row"])]
        ;; This is needed to pass validation.
        [:gmd:dimensionSize {:gco:nilReason "inapplicable"}]
        [:gmd:resolution
         [:gco:Measure {:uom (remove-whitespace unit)}
          (second dimension)]]]])))

(defn generate-spatial-representation-infos
  "Returns spatialRepresentationInfo from HorizontalDataResolution values."
  [c]
  (when-let [group-horizontal-data-resolutions (get-in c [:SpatialExtent :HorizontalSpatialDomain
                                                          :ResolutionAndCoordinateSystem :HorizontalDataResolution])]
    (let [group-horizontal-data-resolutions (dissoc group-horizontal-data-resolutions :VariesResolution :PointResolution)
          horizontal-data-resolutions (map util/remove-nil-keys (flatten (vals group-horizontal-data-resolutions)))]
      [:gmd:spatialRepresentationInfo
       [:gmd:MD_GridSpatialRepresentation
        [:gmd:numberOfDimensions
         [:gco:Integer
          (count (mapcat #(vals (select-keys % [:MinimumXDimension :MaximumXDimension :XDimension
                                                :MinimumYDimension :MaximumYDimension :YDimension]))
                         horizontal-data-resolutions))]]
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
  [{:keys [SwathWidth SwathWidthUnit OrbitPeriod OrbitPeriodUnit InclinationAngle InclinationAngleUnit
           NumberOfOrbits StartCircularLatitude StartCircularLatitudeUnit]}]
  (let [main-string (format "OrbitPeriod: %s OrbitPeriodUnit: %s
                             InclinationAngle: %s InclinationAngleUnit: %s NumberOfOrbits: %s"
                            (util/double->string OrbitPeriod)
                            OrbitPeriodUnit
                            (util/double->string InclinationAngle)
                            InclinationAngleUnit
                            (util/double->string NumberOfOrbits))
        sw-string (when SwathWidth
                    (format "SwathWidth: %s SwathWidthUnit: %s "
                            (util/double->string SwathWidth)
                            SwathWidthUnit))
        scl-string (when StartCircularLatitude
                     (format " StartCircularLatitude: %s StartCircularLatitudeUnit: %s"
                             (util/double->string StartCircularLatitude)
                             StartCircularLatitudeUnit))]
    (str sw-string main-string scl-string)))

(defn- generate-iso-geographic-description
  "This is a generic function that creates an iso geographic desciption
   from the passed in data."
  [id code code-space description]
  [:gmd:geographicElement
   [:gmd:EX_GeographicDescription {:id id}
    [:gmd:geographicIdentifier
     [:gmd:MD_Identifier
      [:gmd:code
       [:gco:CharacterString code]]
      [:gmd:codeSpace
       [:gco:CharacterString code-space]]
      [:gmd:description
       [:gco:CharacterString description]]]]]])

(defn generate-orbit-parameters
  "Returns a geographic element for the orbit parameters"
  [c]
  (when-let [orbit-parameters (-> c :SpatialExtent :OrbitParameters)]
    (generate-iso-geographic-description "OrbitParameters"
                                         (orbit-parameters->encoded-str orbit-parameters)
                                         "gov.nasa.esdis.umm.orbitparameters"
                                         "OrbitParameters")))

(defn- foot-print->encoded-str
  "Encodes the foot print values as a string."
  [{:keys [Footprint FootprintUnit Description]}]
  (if Description
    (format "Footprint: %s FootprintUnit: %s Description: %s" Footprint FootprintUnit Description)
    (format "Footprint: %s FootprintUnit: %s" Footprint FootprintUnit)))

(defn generate-orbit-parameters-foot-prints
  "Returns a geographic element for the orbit parameters Footprints"
  [c]
  (when-let [foot-prints (-> c :SpatialExtent :OrbitParameters :Footprints)]
    (for [x (range (count foot-prints))
           :let [foot-print (nth foot-prints x)]]
      (generate-iso-geographic-description
        (str "OrbitParameter_Footprint_" x)
        (foot-print->encoded-str foot-print)
        "gov.nasa.esdis.umm.orbitparameters_footprint"
        "OrbitParameters_Footprint"))))

(defn spatial-extent-elements
  "Returns a sequence of ISO MENDS elements from the given UMM-C collection record."
  [c]
  (let [spatial (:SpatialExtent c)
        coordinate-system :cartesian
        shapes (spatial-lib-shapes (-> spatial :HorizontalSpatialDomain :Geometry))]
    (->> shapes
         (map (partial umm-s/set-coordinate-system coordinate-system))
         (map gmd/encode))))

(defn- package-horizontal-data-resolutions
  "This method creates a map that encodes the horizontal data resolution type with its value.
   this map is used in the calling function to also add in a count id that is needed in the
   ISO record."
  [[element value]]
  (if (or (= element :VariesResolution)
          (= element :PointResolution))
    {:Element element :Value value}
    (for [res value]
      {:Element element :Value res})))

(defn- prepare-horizontal-data-resolutions
  "This method creates a map that encodes the horizontal data resolution type with its value
   and count that is needed as an identifier in the ISO record."
  [c]
  (let [resolutions (util/remove-nil-keys
                      (get-in c [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem
                                 :HorizontalDataResolution]))
        resolutions (flatten (map #(package-horizontal-data-resolutions %) resolutions))]
    (def resolutions resolutions)
    (for [[id res] (map-indexed vector resolutions)]
      (assoc res :Count id))))

(def generate-horizontal-resolution-code-name-map
  "This map allows the software to programatically encode the ISO horizontal data resolution types."
  {:VariesResolution ["variesresolution" "VariesResolution"]
   :PointResolution ["pointresolution" "PointResolution"]
   :NonGriddedResolutions ["nongriddedresolutions" "NonGriddedResolutions"]
   :NonGriddedRangeResolutions ["nongriddedrangeresolutions" "NonGriddedRangeResolutions"]
   :GriddedResolutions ["griddedresolutions" "GriddedResolutions"]
   :GriddedRangeResolutions ["griddedrangeresolutions" "GriddedRangeResolutions"]
   :GenericResolutions ["genericresolutions" "GenericResolutions"]})

(defn- resolution-and-coordinates-map->string
  "Converts map to parsable string for ISO format."
  [rcmap]
  (when-let [map-keys (keys rcmap)]
   (let [key-val-strings (map #(str (name %)  ": " (get rcmap %)) map-keys)]
     (clojure.string/join " " key-val-strings))))

(defn generate-resolution-and-coordinate-system-horizontal-data-resolutions
  "Returns a sequence of ISO MENDS elements from the given UMM-C collection record."
  [c]
  (when-let [resolutions (prepare-horizontal-data-resolutions c)]
    (for [resolution resolutions
          :let [code (if (or (= (:Element resolution) :VariesResolution)
                            (= (:Element resolution) :PointResolution))
                       (:Value resolution)
                       (-> (:Value resolution)
                           util/remove-nil-keys
                           resolution-and-coordinates-map->string))
                code-name-map (generate-horizontal-resolution-code-name-map (:Element resolution))]]
      (generate-iso-geographic-description
        (str "horizontalresolutionandcoordinatesystem_horizontaldataresolutions" (:Count resolution))
        code
        (str "gov.nasa.esdis.umm.horizontalresolutionandcoordinatesystem_" (first code-name-map))
        (str "HorizontalResolutionAndCoordinateSystem_" (second code-name-map))))))

(defn generate-resolution-and-coordinate-system-local-coords
  "Returns a sequence of ISO MENDS elements from the given UMM-C collection record."
  [c]
  (when-let [local-coords (get-in c [:SpatialExtent :HorizontalSpatialDomain
                                     :ResolutionAndCoordinateSystem :LocalCoordinateSystem])]
    (generate-iso-geographic-description
      "horizontalresolutionandcoordinatesystem_localcoordinatesystem"
      (resolution-and-coordinates-map->string local-coords)
      "gov.nasa.esdis.umm.horizontalresolutionandcoordinatesystem_localcoordinatesystem"
      "HorizontalResolutionAndCoordinateSystem_LocalCoordinateSystem")))

(defn generate-resolution-and-coordinate-system-geodetic-model
  "Returns a sequence of ISO MENDS elements from the given UMM-C collection record."
  [c]
  (when-let [geodetic-model (get-in c [:SpatialExtent :HorizontalSpatialDomain
                                       :ResolutionAndCoordinateSystem :GeodeticModel])]
    (generate-iso-geographic-description
      "horizontalresolutionandcoordinatesystem_geodeticmodel"
      (resolution-and-coordinates-map->string geodetic-model)
      "gov.nasa.esdis.umm.horizontalresolutionandcoordinatesystem_geodeticmodel"
      "HorizontalResolutionAndCoordinateSystem_GeodeticModel")))

(defn generate-resolution-and-coordinate-system-description
  "Returns a sequence of ISO MENDS elements from the given UMM-C collection record."
  [c]
  (when-let [description (get-in c [:SpatialExtent :HorizontalSpatialDomain
                                    :ResolutionAndCoordinateSystem :Description])]
    (generate-iso-geographic-description
      "horizontalresolutionandcoordinatesystem"
      (str "Description: " description)
      "gov.nasa.esdis.umm.horizontalresolutionandcoordinatesystem"
      "HorizontalResolutionAndCoordinateSystem")))

(defn generate-zone-identifier
  "Returns a geographic element for the zone identifier"
  [c]
  (when-let [zone-identifier (get-in c [:SpatialExtent :HorizontalSpatialDomain :ZoneIdentifier])]
    (generate-iso-geographic-description
      "ZoneIdentifier"
      zone-identifier
      "gov.nasa.esdis.umm.zoneidentifier"
      "ZoneIdentifier")))

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
      (generate-iso-geographic-description
        (str "VerticalSpatialDomain" x)
        (vertical-domain->encoded-str vertical-domain)
        "gov.nasa.esdis.umm.verticalspatialdomain"
        "VerticalSpatialDomain"))))
