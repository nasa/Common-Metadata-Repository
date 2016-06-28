(ns cmr.umm-spec.validation.spatial
  "Defines validations for UMM spatial elements."
  (:require [cmr.common.validations.core :as v]
            [cmr.spatial.line-string :as ls]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.point :as point]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.validation :as sv]
            [cmr.umm-spec.validation.utils :as vu]
            [cmr.common.util :as util]
            [cmr.umm-spec.models.common :as common]
            [clojure.string :as string]
            [camel-snake-kebab.core :as csk]))


(def valid-coord-systems
  "Coordinate systems that our valid for umm-spec geometries."
  #{:geodetic :cartesian})

(defn- ->coordinate-system
  "Returns a normalized coordinate system keyword from a string."
  [s]
  (when s
    (get valid-coord-systems (csk/->kebab-case-keyword s))))

(defn- boundary->ring
  "Create a ring from a set of boundary points"
  [coord-sys boundary]
  (rr/ords->ring coord-sys (mapcat #(vector (:Longitude %) (:Latitude %))(:Points boundary))))

(defn- gpolygon->polygon
  "Converts a umm-spec polygon to a spatial lib polygon."
  [coord-sys gpolygon]
  (poly/polygon
   coord-sys
   (concat [(boundary->ring coord-sys (:Boundary gpolygon))]
           ;; holes
           (map boundary->ring (get-in [:ExclusiveZone :Boundaries] gpolygon)))))

(defn polygon-validation
  "Validates a polygon for umm-spec spatial geometry."
  [field-path gpolygon]
  (let [coord-sys (:coord-system gpolygon)]
    (when-let [errors (seq (sv/validate (gpolygon->polygon coord-sys gpolygon)))]
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
    (let [coord-sys (->coordinate-system coord-sys)]
      (-> spatial-extent
          (set-spatial-representation [:HorizontalSpatialDomain :Geometry :GPolygons] coord-sys)
          (set-spatial-representation [:HorizontalSpatialDomain :Geometry :Lines] coord-sys)))))

(defn- umm-spec-point->point
  "Converts a umm-spec point to a spatial lib point."
  [point]
  (let [{:keys [Longitude Latitude]} point]
    (point/point Longitude Latitude)))

(defn point-validation
  "Validates point for umm-spec spatial geometry."
  [field-path point]
  (when-let [errors (seq (sv/validate (umm-spec-point->point point)))]
    {field-path  (map #(str "Spatial validation error: " %) errors)}))

(defn- umm-spec-line->line
  "Converts a umm-spec line to a spatial lib line."
  [coord-sys line]
  (let [points (map umm-spec-point->point (:Points line))]
   (ls/line-string coord-sys points)))

(defn line-validation
  "Validates line for umm-spec spatial geometry."
  [field-path line]
  (let [coord-sys (:coord-system line)]
    (when-let [errors (seq (sv/validate (umm-spec-line->line coord-sys line)))]
      {field-path (map #(str "Spatial validation error: " %) errors)})))

(defn- umm-spec-br->mbr
  "Converts a umm-spec bounding rectangle to a spatial lib mbr."
  [br]
  (let [{:keys [WestBoundingCoordinate NorthBoundingCoordinate EastBoundingCoordinate SouthBoundingCoordinate]} br]
    (mbr/mbr WestBoundingCoordinate NorthBoundingCoordinate EastBoundingCoordinate SouthBoundingCoordinate)))

(defn bounding-rectangle-validation
  "Validates bounding rectangle for umm-spec spatial geometry."
  [field-path br]
  (when-let [errors (seq (sv/validate (umm-spec-br->mbr br)))]
    {field-path (map #(str "Spatial validation error: " %) errors)}))

(def spatial-extent-validation
  "Validation for the SpatialExtent of a umm-spec collection."
  (v/pre-validation set-geometries-spatial-representation
    {:HorizontalSpatialDomain
     {:Geometry
      {:GPolygons (v/every polygon-validation)
       :Points (v/every point-validation)
       :Lines (v/every line-validation)
       :BoundingRectangles (v/every bounding-rectangle-validation)}}}))
