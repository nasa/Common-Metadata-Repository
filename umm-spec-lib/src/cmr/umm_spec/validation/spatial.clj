(ns cmr.umm-spec.validation.spatial
  "Defines validations for UMM spatial elements."
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as string]
   [cmr.common.util :as util]
   [cmr.common.validations.core :as v]
   [cmr.spatial.validation :as sv]
   [cmr.umm-spec.spatial-conversion :as sc]))

(def valid-coord-systems
  "Coordinate systems that are valid for umm-spec geometries."
  #{"GEODETIC" "CARTESIAN"})

(def valid-granule-spatial-representations
  "Granule spatial representations that are valid for umm-spec spatial extents."
  #{"GEODETIC" "CARTESIAN" "ORBIT" "NO_SPATIAL"})

(defn- string->enum-keyword
  "Returns s as keyword if s belongs to enums."
  [s enums]
  (when (and s (get enums s))
    (csk/->kebab-case-keyword s)))

(defn polygon-validation
  "Validates a polygon for umm-spec spatial geometry."
  [field-path gpolygon]
  (let [coord-sys (:coord-system gpolygon)]
    (when-let [errors (seq (sv/validate (sc/gpolygon->polygon coord-sys gpolygon)))]
      {field-path  (map #(str "Spatial validation error: " %) errors)})))

(defn- set-spatial-representation
  "Attach the coordinate system to each geometry (if any) in the spatial extent.
  Geometry here means polygons or lines."
  [spatial-extent path coord-sys]
  (if (get-in spatial-extent path)
    (update-in spatial-extent path (fn [geometry] (map #(assoc % :coord-system coord-sys) geometry)))
    spatial-extent))

(defn- validate-enum
  "Validate if value exists in enums if value is not nil."
  [field-path value enums]
  (when value
    (when-not (string->enum-keyword value enums)
      {field-path
       [(format "Value [%s] not found in enum (possible values: [%s])"
                value
                (string/join "," (sort (map #(format "\"%s\"" %) enums))))]}))) ; Sorting to make order predictable for testing.

(defn- validate-spatial-representation
  "Validate that the granule spatial representation is valid. A granule spatial representation is invalid when:
  Geometry exists and granule spatial representation is nil
  Granule Spatial Representation is not nil and is not one of the specified enums."
 [field-path spatial-extent]
 (let [granule-spatial-representation (:GranuleSpatialRepresentation spatial-extent)
       geometry (util/remove-nil-keys
                 (dissoc (get-in spatial-extent [:HorizontalSpatialDomain :Geometry]) :CoordinateSystem))]
  (if (and (not (empty? geometry))
           (nil? granule-spatial-representation))
    {field-path ["Granule Spatial Representation must be supplied."]}
    (validate-enum field-path granule-spatial-representation valid-granule-spatial-representations))))

(defn- set-geometries-spatial-representation
  "Attach the coordinate system to each geometry to make it available during validation."
  [spatial-extent]
  (when-let [coord-sys (-> spatial-extent :HorizontalSpatialDomain :Geometry :CoordinateSystem)]
    (let [coord-sys (string->enum-keyword coord-sys valid-coord-systems)]
      (-> spatial-extent
          (set-spatial-representation [:HorizontalSpatialDomain :Geometry :GPolygons] coord-sys)
          (set-spatial-representation [:HorizontalSpatialDomain :Geometry :Lines] coord-sys)))))

(defn orbit-collection-has-orbit-parameters
  "Validates the existence of orbit parameters when the granule spatial representation is orbit"
  [field-path spatial-extent]
  (let [{:keys [GranuleSpatialRepresentation OrbitParameters]} spatial-extent]
    (when (and (= GranuleSpatialRepresentation "ORBIT") (nil? OrbitParameters))
      {field-path
       [(str "Orbit Parameters must be defined for a collection "
             "whose granule spatial representation is ORBIT.")]})))

(defn point-validation
  "Validates point for umm-spec spatial geometry."
  [field-path point]
  (when-let [errors (seq (sv/validate (sc/umm-spec-point->point point)))]
    {field-path  (map #(str "Spatial validation error: " %) errors)}))

(defn line-validation
  "Validates line for umm-spec spatial geometry."
  [field-path line]
  (let [coord-sys (:coord-system line)]
    (when-let [errors (seq (sv/validate (sc/umm-spec-line->line coord-sys line)))]
      {field-path (map #(str "Spatial validation error: " %) errors)})))

(defn bounding-rectangle-validation
  "Validates bounding rectangle for umm-spec spatial geometry."
  [field-path br]
  (when-let [errors (seq (sv/validate (sc/umm-spec-br->mbr br)))]
    {field-path (map #(str "Spatial validation error: " %) errors)}))

(def spatial-extent-validation
  "Validation for the SpatialExtent of a umm-spec collection."
  [validate-spatial-representation
   orbit-collection-has-orbit-parameters
   (v/pre-validation set-geometries-spatial-representation
    {:HorizontalSpatialDomain
     {:Geometry
      {:CoordinateSystem #(validate-enum %1 %2 valid-coord-systems)
       :GPolygons (v/every polygon-validation)
       :Points (v/every point-validation)
       :Lines (v/every line-validation)
       :BoundingRectangles (v/every bounding-rectangle-validation)}}})])
