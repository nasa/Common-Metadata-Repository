(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.spatial
  "Functions for parsing UMM spatial records out of ISO 19115-2 XML documents."
  (:require
   [clojure.string :as string]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select text]]
   [cmr.spatial.encoding.gmd :as gmd]
   [cmr.umm-spec.iso19115-2-util :as iso]
   [cmr.umm-spec.migration.spatial-extent-migration :as sp-ext-mg]
   [cmr.umm-spec.models.umm-collection-models :as umm-c]
   [cmr.umm-spec.spatial-conversion :as spatial-conversion]
   [cmr.umm-spec.util :as umm-spec-util]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.distributions-related-url :as iso-shared-distrib]))

(def coordinate-system-xpath
  (str "/gmi:MI_Metadata/gmd:referenceSystemInfo/gmd:MD_ReferenceSystem"
       "/gmd:referenceSystemIdentifier/gmd:RS_Identifier/gmd:code/gco:CharacterString"))

(def geographic-element-xpath
  (str iso/extent-xpath "/gmd:geographicElement"))

(def orbit-string-xpath
  (let [id-xpath "gmd:EX_GeographicDescription/gmd:geographicIdentifier/gmd:MD_Identifier"]
    (str geographic-element-xpath
         "[" id-xpath "/gmd:codeSpace/gco:CharacterString='gov.nasa.esdis.umm.orbitparameters']"
         "/" id-xpath "/gmd:code/gco:CharacterString")))

(def zone-identifier-xpath
  (str "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:extent/gmd:EX_Extent/gmd:geographicElement/gmd:EX_GeographicDescription[@id='ZoneIdentifier']/gmd:geographicIdentifier/gmd:MD_Identifier/gmd:code/gco:CharacterString"))

(def vertical-string-xpath
  (let [id-xpath "gmd:EX_GeographicDescription/gmd:geographicIdentifier/gmd:MD_Identifier"]
    (str geographic-element-xpath
         "[" id-xpath "/gmd:codeSpace/gco:CharacterString='gov.nasa.esdis.umm.verticalspatialdomain']"
         "/" id-xpath "/gmd:code/gco:CharacterString")))

(defmulti umm-spec-shape
  "Returns a UMM-spec shape map from a CMR spatial lib shape type. Dispatches on type of argument."
  (fn [umm-spatial-shape]
    (type umm-spatial-shape)))

(defmethod umm-spec-shape cmr.spatial.point.Point
  [p]
  {:Longitude (:lon p)
   :Latitude  (:lat p)})

(defmethod umm-spec-shape cmr.spatial.mbr.Mbr
  [mbr]
  {:WestBoundingCoordinate  (:west mbr)
   :EastBoundingCoordinate  (:east mbr)
   :NorthBoundingCoordinate (:north mbr)
   :SouthBoundingCoordinate (:south mbr)})

(defmethod umm-spec-shape cmr.spatial.line_string.LineString
  [line]
  {:Points (map umm-spec-shape (:points line))})

(defmethod umm-spec-shape cmr.spatial.polygon.Polygon
  [poly]
  (let [[boundary-ring & interior-rings] (:rings poly)]
    {:Boundary {:Points (map umm-spec-shape (:points boundary-ring))}
     :ExclusiveZone {:Boundaries (for [r interior-rings]
                                   {:Points (map umm-spec-shape (:points r))})}}))

(defn- shape-el?
  "Returns true if XML element is (probably) a shape instead of other geographic information elements."
  [el]
  (empty? (select el "gmd:EX_GeographicDescription")))

(defn parse-geometry
  "Returns UMM GeometryType map from ISO XML document."
  [doc extent-info sanitize?]
  (let [shape-elems    (filter shape-el? (select doc geographic-element-xpath))
        shapes         (flatten (keep gmd/decode shape-elems))
        shapes-by-type (group-by #(.getName (class %)) shapes)
        get-shapes     (fn [k]
                         (map umm-spec-shape (get shapes-by-type k)))
        points (get-shapes "cmr.spatial.point.Point")
        bounding-rectangles (get-shapes "cmr.spatial.mbr.Mbr")
        lines (get-shapes "cmr.spatial.line_string.LineString")
        polygons (get-shapes "cmr.spatial.polygon.Polygon")
        has-shapes? (or (seq points) (seq bounding-rectangles) (seq lines) (seq polygons))]
    {:CoordinateSystem   (or (get extent-info "CoordinateSystem")
                             (value-of doc coordinate-system-xpath)
                             (when (and has-shapes? sanitize?) "CARTESIAN"))
     :Points             points
     :BoundingRectangles bounding-rectangles
     :Lines              lines
     :GPolygons          polygons}))

(defn- parse-orbit-parameters
  "Parses orbit parameters from the ISO XML document. Orbit parameters are encoded in an ISO XML
  document as a single string like this:
  \"SwathWidth: 2.0 Period: 96.7 InclinationAngle: 94.0 NumberOfOrbits: 2.0 StartCircularLatitude: 50.0\""
  [doc]
  (when-let [orbit-string (value-of doc orbit-string-xpath)]
    (into {} (for [[k ^String v] (partition 2 (string/split orbit-string #":? "))]
               [(keyword k) (Double/parseDouble v)]))))

(defn parse-vertical-domains
  "Parse the vertical domain from the ISO XML document. Vertical domains are encoded in an ISO XML
  document as a single string like this:
  \"Type: Some type Value: Some value\""
  [doc]
  (when-let [vertical-domains (select doc vertical-string-xpath)]
    (for [vertical-domain vertical-domains
          :let [vertical-string (value-of (clojure.data.xml/emit-str vertical-domain) "CharacterString")
                type-index (util/get-index-or-nil vertical-string "Type:")
                value-index (util/get-index-or-nil vertical-string "Value:")
                end-index (count vertical-string)
                type (when type-index
                       (iso-shared-distrib/get-substring vertical-string type-index value-index end-index))
                value (when value-index
                       (iso-shared-distrib/get-substring vertical-string value-index end-index))]
          :when (or type value)]
      {:Type type
       :Value value})))

(def geodetic-model-xpath
  (let [id-xpath "gmd:EX_GeographicDescription/gmd:geographicIdentifier/gmd:MD_Identifier"]
    (str geographic-element-xpath
         "[" id-xpath "/gmd:codeSpace/gco:CharacterString='gov.nasa.esdis.umm.horizontalresolutionandcoordinatesystem_geodeticmodel']"
         "/" id-xpath "/gmd:code/gco:CharacterString")))

(def local-coords-xpath
  (let [id-xpath "gmd:EX_GeographicDescription/gmd:geographicIdentifier/gmd:MD_Identifier"]
    (str geographic-element-xpath
         "[" id-xpath "/gmd:codeSpace/gco:CharacterString='gov.nasa.esdis.umm.horizontalresolutionandcoordinatesystem_localcoordinatesystem']"
         "/" id-xpath "/gmd:code/gco:CharacterString")))

(def horizontal-data-resolutions-xpath
  (let [id-xpath "gmd:EX_GeographicDescription/gmd:geographicIdentifier/gmd:MD_Identifier"]
    (str geographic-element-xpath
         "[" id-xpath "/gmd:codeSpace/gco:CharacterString='gov.nasa.esdis.umm.horizontalresolutionandcoordinatesystem_GeographicCoordinateSystems']"
         "/" id-xpath "/gmd:code/gco:CharacterString")))

(def res-and-coord-desc-xpath
  (let [id-xpath "gmd:EX_GeographicDescription/gmd:geographicIdentifier/gmd:MD_Identifier"]
    (str geographic-element-xpath
         "[" id-xpath "/gmd:codeSpace/gco:CharacterString='gov.nasa.esdis.umm.horizontalresolutionandcoordinatesystem']"
         "/" id-xpath "/gmd:code/gco:CharacterString")))

(defn- parse-geodetic-model
  "Parses GeodeticModel from the ISO XML document"
  [doc]
  (when-let [geodetic-model (value-of doc geodetic-model-xpath)]
    (let [horizontal-datum-name-index (util/get-index-or-nil geodetic-model "HorizontalDatumName:")
          ellipsoid-name-index (util/get-index-or-nil geodetic-model "EllipsoidName:")
          semi-major-axis-index (util/get-index-or-nil geodetic-model "SemiMajorAxis:")
          denominator-of-flattening-ratio-index (util/get-index-or-nil geodetic-model "DenominatorOfFlatteningRatio:")
          end-index (count geodetic-model)
          horizontal-datum-name (when horizontal-datum-name-index
                                  (iso-shared-distrib/get-substring-with-sort geodetic-model horizontal-datum-name-index
                                                                              ellipsoid-name-index semi-major-axis-index
                                                                              denominator-of-flattening-ratio-index end-index))
          ellipsoid-name (when ellipsoid-name-index
                           (iso-shared-distrib/get-substring-with-sort geodetic-model ellipsoid-name-index
                                                                       horizontal-datum-name-index semi-major-axis-index
                                                                       denominator-of-flattening-ratio-index end-index))
          semi-major-axis (when semi-major-axis-index
                            (iso-shared-distrib/get-substring-with-sort geodetic-model semi-major-axis-index
                                                                        ellipsoid-name-index horizontal-datum-name-index
                                                                        denominator-of-flattening-ratio-index end-index))
          denominator-of-flattening-ratio-index (when denominator-of-flattening-ratio-index
                                                  (iso-shared-distrib/get-substring-with-sort geodetic-model denominator-of-flattening-ratio-index
                                                                                              ellipsoid-name-index horizontal-datum-name-index
                                                                                              semi-major-axis-index end-index))]
      (when (or horizontal-datum-name ellipsoid-name semi-major-axis denominator-of-flattening-ratio-index)
        (umm-c/map->GeodeticModelType
          (util/remove-nil-keys
            {:HorizontalDatumName (when-not (empty? horizontal-datum-name)
                                    horizontal-datum-name)
             :EllipsoidName (when-not (empty? ellipsoid-name)
                              ellipsoid-name)
             :SemiMajorAxis (when-not (empty? semi-major-axis)
                              (read-string semi-major-axis))
             :DenominatorOfFlatteningRatio (when-not (empty? denominator-of-flattening-ratio-index)
                                             (read-string denominator-of-flattening-ratio-index))}))))))

(defn- parse-local-coord-sys
  "Parses LocalCoordinateSystem from the ISO XML document"
  [doc]
  (when-let [local-coords (value-of doc local-coords-xpath)]
    (let [geo-reference-information-index (util/get-index-or-nil
                                           local-coords
                                           "GeoReferenceInformation:")
          description-index (util/get-index-or-nil local-coords "Description:")
          end-index (count local-coords)
          geo-reference-information (when geo-reference-information-index
                                      (iso-shared-distrib/get-substring-with-sort
                                       local-coords geo-reference-information-index
                                       description-index end-index))
          description (when description-index
                           (iso-shared-distrib/get-substring-with-sort
                            local-coords description-index
                            geo-reference-information-index end-index))]
      (when (or geo-reference-information description)
        (umm-c/map->LocalCoordinateSystemType
         (util/remove-nil-keys
          {:GeoReferenceInformation (when-not (empty? geo-reference-information)
                                      geo-reference-information)
           :Description (when-not (empty? description)
                          description)}))))))

(defn- get-substring-with-index
  "With given index and indexes, run get-substring-with-sort with proper args."
  [resolution-string indexes ele-index]
  (apply
    iso-shared-distrib/get-substring-with-sort
    resolution-string
    ele-index
    (remove #(= % ele-index) indexes)))

(defn- group-resolutions
  "Returns horizontal-data-resolutions in groups."
  [horizontal-data-resolutions]
  (when (seq horizontal-data-resolutions)
    (let [varies (first (sp-ext-mg/get-enum-group horizontal-data-resolutions umm-spec-util/varies))
          point (first (sp-ext-mg/get-enum-group horizontal-data-resolutions umm-spec-util/point))
          non-gridded (sp-ext-mg/get-enum-group horizontal-data-resolutions umm-spec-util/non-gridded)
          non-gridded-range (sp-ext-mg/get-enum-group horizontal-data-resolutions umm-spec-util/non-gridded-range)
          gridded (sp-ext-mg/get-enum-group horizontal-data-resolutions umm-spec-util/gridded)
          generic (sp-ext-mg/get-enum-group horizontal-data-resolutions umm-spec-util/not-provided)
          gridded-range (sp-ext-mg/get-enum-group horizontal-data-resolutions umm-spec-util/gridded-range)]
     (umm-c/map->HorizontalDataResolutionType
       {:VariesResolution (when (seq varies)
                            (umm-c/map->HorizontalDataResolutionVariesType
                              varies))
        :PointResolution (when (seq point)
                           (umm-c/map->HorizontalDataResolutionPointType
                             point))
        :NonGriddedResolutions (when (seq non-gridded)
                                 (map umm-c/map->HorizontalDataResolutionNonGriddedType
                                      (sp-ext-mg/remove-enum-from-group non-gridded)))
        :NonGriddedRangeResolutions (when (seq non-gridded-range)
                                      (map umm-c/map->HorizontalDataResolutionNonGriddedRangeType
                                           (sp-ext-mg/remove-enum-from-group non-gridded-range)))
        :GriddedResolutions (when (seq gridded)
                              (map umm-c/map->HorizontalDataResolutionGriddedType
                                   (sp-ext-mg/remove-enum-from-group gridded)))
        :GenericResolutions (when (seq generic)
                              (map umm-c/map->HorizontalDataGenericResolutionType
                                   (sp-ext-mg/remove-enum-from-group generic)))
       :GriddedRangeResolutions (when (seq gridded-range)
                                  (map umm-c/map->HorizontalDataResolutionGriddedRangeType
                                       (sp-ext-mg/remove-enum-from-group gridded-range)))}))))

(defn- parse-horizontal-data-resolutions
  "Parses HorizontalDataResolution from the ISO XML document"
  [doc]
  (when-let [horizontal-data-resolutions (select doc horizontal-data-resolutions-xpath)]
    (for [horizontal-data-resolution horizontal-data-resolutions
          :let [resolution-string (first (:content horizontal-data-resolution))
                unit-index (util/get-index-or-nil resolution-string "Unit:")
                horizontal-resolution-processing-level-enum-index (util/get-index-or-nil
                                                                   resolution-string
                                                                   "HorizontalResolutionProcessingLevelEnum:")
                minimum-xdimension-index (util/get-index-or-nil resolution-string "MinimumXDimension:")
                maximum-xdimension-index (util/get-index-or-nil resolution-string "MaximumXDimension:")
                minimum-ydimension-index (util/get-index-or-nil resolution-string "MinimumYDimension:")
                maximum-ydimension-index (util/get-index-or-nil resolution-string "MaximumYDimension:")
                scan-direction-index (util/get-index-or-nil resolution-string "ScanDirection:")
                viewing-angle-type-index (util/get-index-or-nil resolution-string "ViewingAngleType:")
                end-index (count resolution-string)
                ;;x/ydimension won't appear together with min/max x/ydimensions and
                xdimension-index (when (and (nil? minimum-xdimension-index)
                                            (nil? maximum-xdimension-index))
                                   (util/get-index-or-nil resolution-string "XDimension:"))
                ydimension-index (when (and (nil? minimum-ydimension-index)
                                            (nil? maximum-ydimension-index))
                                   (util/get-index-or-nil resolution-string "YDimension:"))
                indexes [viewing-angle-type-index scan-direction-index maximum-xdimension-index
                         minimum-xdimension-index maximum-ydimension-index minimum-ydimension-index
                         xdimension-index ydimension-index horizontal-resolution-processing-level-enum-index
                         unit-index end-index]
                xdimension (when xdimension-index
                             (get-substring-with-index resolution-string indexes xdimension-index))
                ydimension (when ydimension-index
                             (get-substring-with-index resolution-string indexes ydimension-index))
                unit (when unit-index
                       (get-substring-with-index resolution-string indexes unit-index))
                minimum-ydimension (when minimum-ydimension-index
                                     (get-substring-with-index resolution-string indexes minimum-ydimension-index))
                maximum-ydimension (when maximum-ydimension-index
                                     (get-substring-with-index resolution-string indexes maximum-ydimension-index))
                minimum-xdimension (when minimum-xdimension-index
                                     (get-substring-with-index resolution-string indexes minimum-xdimension-index))
                maximum-xdimension (when maximum-xdimension-index
                                     (get-substring-with-index resolution-string indexes maximum-xdimension-index))
                scan-direction (when scan-direction-index
                                 (get-substring-with-index resolution-string indexes scan-direction-index))
                viewing-angle-type (when viewing-angle-type-index
                                     (get-substring-with-index resolution-string indexes viewing-angle-type-index))
                horizontal-resolution-processing-level-enum (when horizontal-resolution-processing-level-enum-index
                                                              (get-substring-with-index resolution-string indexes horizontal-resolution-processing-level-enum-index))]]
      (util/remove-nil-keys
        {:XDimension (when (seq xdimension)
                       (read-string xdimension))
         :YDimension (when (seq ydimension)
                       (read-string ydimension))
         :Unit (when (seq unit)
                 unit)
         :MinimumYDimension (when (seq minimum-ydimension)
                              (read-string minimum-ydimension))
         :MaximumYDimension (when (seq maximum-ydimension)
                              (read-string maximum-ydimension))
         :MinimumXDimension (when (seq minimum-xdimension)
                              (read-string minimum-xdimension))
         :MaximumXDimension (when (seq maximum-xdimension)
                              (read-string maximum-xdimension))
         :ScanDirection (when (seq scan-direction)
                          scan-direction)
         :ViewingAngleType (when (seq viewing-angle-type)
                             viewing-angle-type)
         :HorizontalResolutionProcessingLevelEnum (when (seq horizontal-resolution-processing-level-enum)
                                                    horizontal-resolution-processing-level-enum)}))))

(defn- parse-horizontal-spatial-domain
  "Parse the horizontal domain from the ISO XML document. Horizontal domains are encoded in an ISO XML"
  [doc extent-info sanitize?]
  (let [description (when-let [description-string
                               (value-of doc res-and-coord-desc-xpath)]
                      (let [description-index
                            (util/get-index-or-nil description-string "Description:")]
                        (iso-shared-distrib/get-substring-with-sort
                         description-string description-index (count description-string))))]
    (util/remove-nil-keys
     {:Geometry (parse-geometry doc extent-info sanitize?)
      :ZoneIdentifier (value-of doc zone-identifier-xpath)
      :ResolutionAndCoordinateSystem (util/remove-nil-keys
                                      {:Description (iso/safe-trim description)
                                       :GeodeticModel (parse-geodetic-model doc)
                                       :LocalCoordinateSystem (parse-local-coord-sys doc)
                                       :HorizontalDataResolution (group-resolutions (parse-horizontal-data-resolutions doc))})})))

(defn parse-spatial
  "Returns UMM SpatialExtentType map from ISO XML document."
  [doc extent-info sanitize?]
  {:SpatialCoverageType (get extent-info "SpatialCoverageType")
   :GranuleSpatialRepresentation (or (get extent-info "SpatialGranuleSpatialRepresentation")
                                     (when sanitize? "NO_SPATIAL"))
   :HorizontalSpatialDomain (parse-horizontal-spatial-domain doc extent-info sanitize?)
   :VerticalSpatialDomains (spatial-conversion/drop-invalid-vertical-spatial-domains
                            (parse-vertical-domains doc))
   :OrbitParameters (parse-orbit-parameters doc)})
