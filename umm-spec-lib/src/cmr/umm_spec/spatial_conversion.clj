(ns cmr.umm-spec.spatial-conversion
  "Defines functions that convert umm spec spatial types to spatial lib spatial shapes."
  (:require
   [cmr.spatial.line-string :as ls]
   [cmr.spatial.mbr :as mbr]
   [cmr.spatial.point :as point]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.ring-relations :as rr]))

(def valid-tile-identification-system-names
  "Valid names for TilingIdentificationSystemName as stated in CMR-3675"
  ["CALIPSO"
   "MISR"
   "MODIS Tile EASE"
   "MODIS Tile SIN"
   "WELD Alaska Tile"
   "WELD CONUS Tile"
   "WRS-1"
   "WRS-2"])

(defn tile-id-system-name-is-valid?
  "Return whether or not the given TileIdentificationSystemName is one of the
   valid-tile-identification-system-names"
  [tile-system-id-name]
  (some #(= tile-system-id-name %) valid-tile-identification-system-names))

(defn translate-tile-id-system-name
  "Return nil or equivalent value if the given name does not match any
   in the list of valid ones"
  [tile-identification-system-name]
  (when (tile-id-system-name-is-valid? tile-identification-system-name)
    tile-identification-system-name))

(defn expected-tiling-id-systems-name
  "Translate TilingIdentificationSystemNames in accordance with UMM Spec v1.10"
  [tiling-identification-systems]
  (let [tiling-id-systems (mapv
                           (fn [tiling-id-system]
                             (update tiling-id-system
                                     :TilingIdentificationSystemName
                                     translate-tile-id-system-name))
                           tiling-identification-systems)]
    (when-not (empty? tiling-id-systems)
      tiling-id-systems)))

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
