(ns cmr.umm-spec.validation.spatial
  "Defines validations for UMM spatial elements."
  (:require
   [camel-snake-kebab.core :as csk]
   [cmr.common.validations.core :as v]
   [cmr.spatial.validation :as sv]
   [cmr.umm-spec.spatial-conversion :as sc]))

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

(defn- set-geometries-spatial-representation
  "Attach the coordinate system to each geometry to make it available during validation."
  [spatial-extent]
  (when-let [coord-sys (-> spatial-extent :HorizontalSpatialDomain :Geometry :CoordinateSystem)]
    (let [coord-sys (csk/->kebab-case-keyword coord-sys)]
      (-> spatial-extent
          (set-spatial-representation [:HorizontalSpatialDomain :Geometry :GPolygons] coord-sys)
          (set-spatial-representation [:HorizontalSpatialDomain :Geometry :Lines] coord-sys)))))

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
  [(v/pre-validation set-geometries-spatial-representation
    {:HorizontalSpatialDomain
     {:Geometry
      {:GPolygons (v/every polygon-validation)
       :Lines (v/every line-validation)
       :BoundingRectangles (v/every bounding-rectangle-validation)}}})])
