(ns cmr.umm-spec.spatial-conversion
  "Defines functions that convert umm spec spatial types to spatial lib spatial shapes."
  (:require
   [cmr.spatial.line-string :as ls]
   [cmr.spatial.mbr :as mbr]
   [cmr.spatial.point :as point]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.ring-relations :as rr]))

(defn boundary->ring
  "Create a ring from a set of boundary points"
  [coord-sys boundary]
  (rr/ords->ring coord-sys (mapcat #(vector (:Longitude %) (:Latitude %))(:Points boundary))))

(defn gpolygon->polygon
  "Converts a umm-spec polygon to a spatial lib polygon."
  [coord-sys gpolygon]
  (poly/polygon
   coord-sys
   (concat [(boundary->ring coord-sys (:Boundary gpolygon))]
           ;; holes
           (map #(boundary->ring coord-sys %) (get-in gpolygon [:ExclusiveZone :Boundaries])))))

(defn umm-spec-point->point
  "Converts a umm-spec point to a spatial lib point."
  [point]
  (let [{:keys [Longitude Latitude]} point]
    (point/point Longitude Latitude)))

(defn umm-spec-line->line
  "Converts a umm-spec line to a spatial lib line."
  [coord-sys line]
  (let [points (map umm-spec-point->point (:Points line))]
   (ls/line-string coord-sys points)))

(defn umm-spec-br->mbr
  "Converts a umm-spec bounding rectangle to a spatial lib mbr."
  [br]
  (let [{:keys [WestBoundingCoordinate NorthBoundingCoordinate EastBoundingCoordinate SouthBoundingCoordinate]} br]
    (mbr/mbr WestBoundingCoordinate NorthBoundingCoordinate EastBoundingCoordinate SouthBoundingCoordinate)))
