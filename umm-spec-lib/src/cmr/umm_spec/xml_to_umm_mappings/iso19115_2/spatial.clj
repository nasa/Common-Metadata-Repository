(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.spatial
  "Functions for parsing UMM spatial records out of ISO 19115-2 XML documents."
  (:require
   [clojure.string :as string]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select text]]
   [cmr.spatial.encoding.gmd :as gmd]
   [cmr.umm-spec.iso19115-2-util :as iso-util]
   [cmr.umm-spec.models.umm-collection-models :as umm-c]
   [cmr.umm-spec.spatial-conversion :as spatial-conversion]
   [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.spatial :refer [generate-horizontal-resolution-code-name-map]]
   [cmr.umm-spec.util :as umm-spec-util]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.shared-iso-parsing-util :as iso-xml-parsing-util]))

(def coordinate-system-xpath
  (str "/gmi:MI_Metadata/gmd:referenceSystemInfo/gmd:MD_ReferenceSystem"
       "/gmd:referenceSystemIdentifier/gmd:RS_Identifier/gmd:code/gco:CharacterString"))

(def spatial-extent-xpath
  (str "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification"
       "/gmd:extent/gmd:EX_Extent[@id='boundingExtent']"))

(def geographic-element-xpath
  (str spatial-extent-xpath
       "/gmd:geographicElement"))

(def orbit-string-xpath
  (let [id-xpath "gmd:EX_GeographicDescription/gmd:geographicIdentifier/gmd:MD_Identifier"]
    (str geographic-element-xpath
         "[" id-xpath "/gmd:codeSpace/gco:CharacterString='gov.nasa.esdis.umm.orbitparameters']"
         "/" id-xpath "/gmd:code/gco:CharacterString")))

(def orbit-foot-prints-xpath
  (let [id-xpath "gmd:EX_GeographicDescription/gmd:geographicIdentifier/gmd:MD_Identifier"]
    (str geographic-element-xpath
         "[" id-xpath "/gmd:codeSpace/gco:CharacterString='gov.nasa.esdis.umm.orbitparameters_footprint']"
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

(defn parse-coordinate-system
  "Parses the CoordinateSystem from the ISO XML document. If the value is a string that contains
   an EPSG code then that collection is GEODETIC."
  [doc]
  (when-let [doc-coord-sys (value-of doc coordinate-system-xpath)]
    (if (string/includes? (string/lower-case doc-coord-sys) "epsg" )
      "GEODETIC"
      doc-coord-sys)))

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
                             (parse-coordinate-system doc)
                             (when (and has-shapes? sanitize?) "CARTESIAN"))
     :Points             points
     :BoundingRectangles bounding-rectangles
     :Lines              lines
     :GPolygons          polygons}))

(defn- parse-orbit-parameters
  "Parses orbit parameters from the ISO XML document. Orbit parameters are encoded in an ISO XML
  document as a single string like this:
  \"SwathWidth: 390 SwathWidthUnit: Kilometer OrbitPeriod: 98 OrbitPeriodUnit: Decimal Minute
  InclinationAngle: 98 InclinationAngleUnit: Degree NumberOfOrbits: 1 StartCircularLatitude: 0
  StartCircularLatitudeUnit: Degree\"
  Note: The string could either constain Period or OrbitPeriod."
  [doc]
  (when-let [orbit-string (value-of doc orbit-string-xpath)]
    (iso-xml-parsing-util/convert-iso-description-string-to-map
     orbit-string
     (re-pattern
      "SwathWidth:|SwathWidthUnit:|Period:|OrbitPeriod:|OrbitPeriodUnit:|InclinationAngle:|InclinationAngleUnit:|StartCircularLatitude:|StartCircularLatitudeUnit:|NumberOfOrbits:"))))

(defn- parse-orbit-parameters-foot-prints
  "Parse orbit parameter foot prints from the ISO XML document. Foot prints are encoded in an ISO
  XML document as a single string like this:
  \"Footprint: 100 FootprintUnit: Kilometer Description: The leading footprint\""
  [doc]
  (when-let [orbit-foot-prints (select doc orbit-foot-prints-xpath)]
    (for [orbit-foot-print orbit-foot-prints
          :let [ofp-string (value-of (clojure.data.xml/emit-str orbit-foot-print) "CharacterString")
                ofp-map (iso-xml-parsing-util/convert-iso-description-string-to-map
                          ofp-string
                          (re-pattern "Footprint:|FootprintUnit:|Description:"))]
          :when (not-empty ofp-map)]
      ofp-map)))

(defn parse-vertical-domains
  "Parse the vertical domain from the ISO XML document. Vertical domains are encoded in an ISO XML
  document as a single string like this:
  \"Type: Some type Value: Some value\""
  [doc]
  (when-let [vertical-domains (select doc vertical-string-xpath)]
    (for [vertical-domain vertical-domains
          :let [vertical-string (value-of (clojure.data.xml/emit-str vertical-domain) "CharacterString")
                vertical-map
                  (iso-xml-parsing-util/convert-iso-description-string-to-map
                    vertical-string
                    (re-pattern "Type:|Value:"))]
          :when (not-empty vertical-map)]
      vertical-map)))

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
  (str geographic-element-xpath
       "/gmd:EX_GeographicDescription[contains(@id, 'horizontalresolutionandcoordinatesystem_horizontaldataresolutions')]"
       "/gmd:geographicIdentifier/gmd:MD_Identifier"))

(def res-and-coord-desc-xpath
  (let [id-xpath "gmd:EX_GeographicDescription/gmd:geographicIdentifier/gmd:MD_Identifier"]
    (str geographic-element-xpath
         "[" id-xpath "/gmd:codeSpace/gco:CharacterString='gov.nasa.esdis.umm.horizontalresolutionandcoordinatesystem']"
         "/" id-xpath "/gmd:code/gco:CharacterString")))

(def geodetic-model-string-field-re-pattern
  "Returns the pattern that matches all the related fields in description-string"
  (re-pattern "HorizontalDatumName:|EllipsoidName:|SemiMajorAxis:|DenominatorOfFlatteningRatio:"))

(defn- parse-geodetic-model
  "Parses GeodeticModel from the ISO XML document"
  [doc]
  (when-let [geodetic-model (value-of doc geodetic-model-xpath)]
    (let [m (iso-xml-parsing-util/convert-iso-description-string-to-map
              geodetic-model
              geodetic-model-string-field-re-pattern)
          horizontal-datum-name (:HorizontalDatumName m)
          ellipsoid-name (:EllipsoidName m)
          semi-major-axis (:SemiMajorAxis m)
          denominator-of-flattening-ratio (:DenominatorOfFlatteningRatio m)]
      (when (or horizontal-datum-name ellipsoid-name semi-major-axis denominator-of-flattening-ratio)
        (umm-c/map->GeodeticModelType
          {:HorizontalDatumName (when-not (empty? horizontal-datum-name)
                                  horizontal-datum-name)
           :EllipsoidName (when-not (empty? ellipsoid-name)
                            ellipsoid-name)
           :SemiMajorAxis (when-not (empty? semi-major-axis)
                            (read-string semi-major-axis))
           :DenominatorOfFlatteningRatio (when-not (empty? denominator-of-flattening-ratio)
                                           (read-string denominator-of-flattening-ratio))})))))

(def local-coord-sys-string-field-re-pattern
  "Returns the pattern that matches all the related fields in description-string"
  (re-pattern "GeoReferenceInformation:|Description:"))

(defn- parse-local-coord-sys
  "Parses LocalCoordinateSystem from the ISO XML document"
  [doc]
  (when-let [local-coords (value-of doc local-coords-xpath)]
    (let [m (iso-xml-parsing-util/convert-iso-description-string-to-map
              local-coords
              local-coord-sys-string-field-re-pattern)
          geo-reference-information (:GeoReferenceInformation m)
          description (:Description m)]
      (when (or geo-reference-information description)
        (umm-c/map->LocalCoordinateSystemType
          {:GeoReferenceInformation (when-not (empty? geo-reference-information)
                                      geo-reference-information)
           :Description (when-not (empty? description)
                          description)})))))

(defmacro horizontal-resolution-code-name-to-key-map
  "This macro takes the map used in cmr.umm-spec.umm-to-xml-mappings.iso19115-2.spatial/
   generate-horizontal-resolution-code-name-map and converts the values into the keys and the
   original key as a value. The resulting map looks like:
   {\"variesresolution\" :VariesResolution
    \"VariesResolution\" :VariesResolution
    etc.}
   This map is used by the parsing code to construct a UMM-C resolutions structure based on what
   is contained in the ISO record."
  []
  (into {}
    (for [x (clojure.set/map-invert generate-horizontal-resolution-code-name-map)]
      {(first (first x)) (second x)
       (second (first x)) (second x)})))

(defn- parse-xpath-result-horizontal-resolution-key-name-map
  "This function takes an XPath parsed element resolution as input - example: #clojure.data.xml.Element{
     :tag :description, :attrs {}, :content (#clojure.data.xml.Element{
     :tag :CharacterString, :attrs {},
     :content (HorizontalResolutionAndCoordinateSystem_GenericResolutions)})})
   and builds on the passed in map like:
  {:description \"HorizontalResolutionAndCoordinateSystem_GenericResolutions\"}.
  This map is used later to programatically pull out the UMM-C class name. in this example it
  would be GenericResolutions"
  [xpath-parsed-resolution-element map]
  (def res1 xpath-parsed-resolution-element)
  (let [x (get-in xpath-parsed-resolution-element [:tag])]
    (assoc map x (-> xpath-parsed-resolution-element
                     (get-in [:content])
                     first
                     (get-in [:content])
                     first))))

(defn- get-horizontal-resolution-key-name
  "Find the key (the UMM-C type) for each different type of resolution that can be
   used. The passed in map is the map that was generated by
   parse-xpath-result-horizontal-resolution-key-name-map. Example:
   {:codeSpace \"HorizontalResolutionAndCoordinateSystem_genericresolutions\"
    :description \"HorizontalResolutionAndCoordinateSystem_GenericResolutions\"}
   First look at :codeSpace to see if it
   exists to parse out the UMM-C type, otherwise look at :description."
  [m]
  (if (:codeSpace m)
       (-> (:codeSpace m)
           name
           (string/split #"_")
           second
           ;; The threads macro will insert the string so it will look like
           ;; the following ((horizontal-resolution-code-name-to-key-map) "genericresolutions")
           ;; and the correct value :GenericResolutions is passed back.
           ((horizontal-resolution-code-name-to-key-map))))
       (when (:description m)
         (-> (:description m)
             name
             (string/split #"_")
             second
             ;; The threads macro will insert the string so it will look like
             ;; the following ((horizontal-resolution-code-name-to-key-map) "genericresolutions")
             ;; and the correct value :GenericResolutions is passed back.
             ((horizontal-resolution-code-name-to-key-map)))))

(def horizontal-resolution-description-string-field-re-pattern
  "Returns the pattern that matches all the related fields in description-string"
  (re-pattern "XDimension:|MinimumXDimension:|MaximumXDimension:|YDimension:|MinimumYDimension:|MaximumYDimension:|Unit:|ViewingAngleType:|ScanDirection:"))

(def horizontal-resolution-keys-represent-numbers
  "Returns the list of keys where the values are numbers."
  '(:XDimension :MinimumXDimension :MaximumXDimension :YDimension :MinimumYDimension :MaximumYDimension))

(defn- ummify-horizontal-resolution
  "Converts the passed in map (m) into a UMM-C defrecord for one of the horizontal data resolution
   sub elements that is defined by the passed in key (k)."
  [k m]
  (case k
    :NonGriddedResolutions (umm-c/map->HorizontalDataResolutionNonGriddedType m)
    :NonGriddedRangeResolutions (umm-c/map->HorizontalDataResolutionNonGriddedRangeType m)
    :GriddedResolutions (umm-c/map->HorizontalDataResolutionGriddedType m)
    :GriddedRangeResolutions (umm-c/map->HorizontalDataResolutionGriddedRangeType m)
    :GenericResolutions (umm-c/map->HorizontalDataGenericResolutionType m)
    nil))

(defn get-single-resolution-umm-structure
  "Parses the passed in XPath XML structure into a horizontal data resolution UMM-C typed map."
  [parsed-xml-structure]
  (let [parsed-xml (into {}
                     (map #(parse-xpath-result-horizontal-resolution-key-name-map % {})
                          (:content parsed-xml-structure)))
        horizontal-res-key-name (get-horizontal-resolution-key-name parsed-xml)]
    (if (or (= horizontal-res-key-name :VariesResolution)
            (= horizontal-res-key-name :PointResolution))
      {horizontal-res-key-name (:code parsed-xml)};)))
      {horizontal-res-key-name
       (->> (iso-xml-parsing-util/convert-iso-description-string-to-map
              (:code parsed-xml)
              horizontal-resolution-description-string-field-re-pattern
              horizontal-resolution-keys-represent-numbers)
            (ummify-horizontal-resolution horizontal-res-key-name)
            util/remove-nil-keys)})))

(defn- group-same-structures
  "This function groups a list of maps together based on a passed in key."
  [k list-of-maps ]
  (->> list-of-maps
       (map k)
       (remove nil?)))

(defn parse-horizontal-data-resolutions
  "Parses HorizontalDataResolution from the ISO XML document"
  [doc]
  (let [horizontal-data-resolutions (select doc horizontal-data-resolutions-xpath)
        list-of-maps (for [horizontal-data-resolution horizontal-data-resolutions]
                       (get-single-resolution-umm-structure horizontal-data-resolution))
        json-horizontal-data-resolutions
          (util/remove-nil-keys
            (umm-c/map->HorizontalDataResolutionType
              {:VariesResolution (first
                                   (group-same-structures :VariesResolution list-of-maps))
               :PointResolution (first
                                  (group-same-structures :PointResolution list-of-maps))
               :NonGriddedResolutions (seq
                                        (group-same-structures :NonGriddedResolutions list-of-maps))
               :NonGriddedRangeResolutions (seq
                                             (group-same-structures :NonGriddedRangeResolutions list-of-maps))
               :GriddedResolutions (seq
                                     (group-same-structures :GriddedResolutions list-of-maps))
               :GriddedRangeResolutions (seq
                                          (group-same-structures :GriddedRangeResolutions list-of-maps))
               :GenericResolutions (seq (group-same-structures :GenericResolutions list-of-maps))}))]
    (when-not (empty? json-horizontal-data-resolutions)
      json-horizontal-data-resolutions)))

(defn- parse-horizontal-spatial-domain
  "Parse the horizontal domain from the ISO XML document. Horizontal domains are encoded in an ISO XML"
  [doc extent-info sanitize?]
  (let [description-string (value-of doc res-and-coord-desc-xpath)
        m (when description-string
            (iso-xml-parsing-util/convert-iso-description-string-to-map description-string (re-pattern "Description:")))
        description (:Description m)]
    (util/remove-nil-keys
     {:Geometry (parse-geometry doc extent-info sanitize?)
      :ZoneIdentifier (value-of doc zone-identifier-xpath)
      :ResolutionAndCoordinateSystem (util/remove-nil-keys
                                      {:Description (iso-util/safe-trim description)
                                       :GeodeticModel (parse-geodetic-model doc)
                                       :LocalCoordinateSystem (parse-local-coord-sys doc)
                                       :HorizontalDataResolution (parse-horizontal-data-resolutions doc)})})))

(defn parse-spatial
  "Returns UMM SpatialExtentType map from ISO XML document."
  [doc sanitize?]
  (let [extent-info (iso-util/get-extent-info-map doc spatial-extent-xpath)]
    {:SpatialCoverageType (get extent-info "SpatialCoverageType")
     :GranuleSpatialRepresentation (or (get extent-info "SpatialGranuleSpatialRepresentation")
                                       (when sanitize? "NO_SPATIAL"))
     :HorizontalSpatialDomain (parse-horizontal-spatial-domain doc extent-info sanitize?)
     :VerticalSpatialDomains (spatial-conversion/drop-invalid-vertical-spatial-domains
                              (parse-vertical-domains doc))
     :OrbitParameters (as->(parse-orbit-parameters doc) op
                           ;; OrbitPeriod could be either in :Period or OrbitPeriod
                           (if (:Period op)
                             (assoc op :OrbitPeriod (:Period op))
                             op)
                           ;; add the assumed units if the corresponding fields exist but the corresponding units don't.
                           (if (and (:SwathWidth op) (not (:SwathWidthUnit op)))
                             (assoc op :SwathWidthUnit "Kilometer")
                             op)
                           (if (and (:OrbitPeriod op) (not (:OrbitPeriodUnit op)))
                             (assoc op :OrbitPeriodUnit "Decimal Minute")
                             op)
                           (if (:InclinationAngle op)
                             (assoc op :InclinationAngleUnit "Degree")
                             op)
                           (if (:StartCircularLatitude op)
                             (assoc op :StartCircularLatitudeUnit "Degree")
                             op)
                           (dissoc op :Period)
                           (assoc op :Footprints (parse-orbit-parameters-foot-prints doc)))}))
