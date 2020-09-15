(ns cmr.umm-spec.spatial-conversion
  "Defines functions that convert umm spec spatial types to spatial lib spatial shapes."
  (:require
   [clojure.string :as string]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :as xml-parse]
   [cmr.spatial.line-string :as ls]
   [cmr.spatial.mbr :as mbr]
   [cmr.spatial.point :as point]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.ring-relations :as rr]))

(def valid-tile-identification-system-names
  "Valid names for TilingIdentificationSystemName"
  ["CALIPSO"
   "MISR"
   "MODIS Tile EASE"
   "MODIS Tile SIN"
   "WELD Alaska Tile"
   "WELD CONUS Tile"
   "WRS-1"
   "WRS-2"
   "Military Grid Reference System"])

(def upcase-valid-tile-identification-system-names
  "Upcase values to allow for case-insenitive validation"
  (map util/safe-uppercase valid-tile-identification-system-names))

(defn tile-id-system-name-is-valid?
  "Return whether or not the given TileIdentificationSystemName is one of the
   valid-tile-identification-system-names"
  [tile-system-id-name]
  (some #(= (util/safe-uppercase tile-system-id-name) %) upcase-valid-tile-identification-system-names))

(defn translate-tile-id-system-name
  "Return nil or equivalent value if the given name does not match any
   in the list of valid ones"
  [tile-identification-system-name]
  (when (tile-id-system-name-is-valid? tile-identification-system-name)
    (util/match-enum-case tile-identification-system-name valid-tile-identification-system-names)))

(defn expected-tiling-id-systems-name
  "Translate TilingIdentificationSystemNames in accordance with UMM Spec v1.10"
  [tiling-identification-systems]
  (->> tiling-identification-systems
       (mapv
        #(update % :TilingIdentificationSystemName translate-tile-id-system-name))
       seq))

(defn filter-and-translate-tiling-id-systems
  "Drop invalid TilingIdentificationSystems, and ensure that valid ones are cased
   properly."
  [tiling-identification-systems]
  (->> tiling-identification-systems
       (filter #(tile-id-system-name-is-valid? (:TilingIdentificationSystemName %)))
       expected-tiling-id-systems-name))

(def valid-vertical-spatial-domain-types
  "Valid values for VerticalSpatialDomainType according to UMM spec v1.10.0"
  ["Atmosphere Layer"
   "Maximum Altitude"
   "Maximum Depth"
   "Minimum Altitude"
   "Minimum Depth"])

(def upcase-valid-vertical-spatial-domain-types
  "Upcase values to allow for case-insenitive validation"
  (map util/safe-uppercase valid-vertical-spatial-domain-types))

(defn vertical-spatial-domain-type-is-valid?
  "Return true or false based on whether or not the given value matches
   one of the valid values in valid-vertical-spatial-domain-types."
  [vs-domain-type]
  (some #(= (util/safe-uppercase vs-domain-type) %)
        upcase-valid-vertical-spatial-domain-types))

(defn drop-invalid-vertical-spatial-domains
  "Any VerticalSpatialDomain with a Type not in valid-vertical-spatial-domain-types
   is not considered valid according to UMM spec v1.10.0 and should be dropped.
   This behavior will eventually generate errors"
  [vertical-spatial-domains]
  (->> vertical-spatial-domains
       (filter #(vertical-spatial-domain-type-is-valid? (:Type %)))
       (map (fn [domain]
              (update domain :Type #(util/match-enum-case
                                     % valid-vertical-spatial-domain-types))))
       seq))

(defn convert-vertical-spatial-domains-from-xml
  "Given a name to key into an xml file, extract Vertical Spatial Domains
   and drop those that are invalid."
  [vertical-spatial-domains]
  (->> vertical-spatial-domains
       (map #(xml-parse/fields-from % :Type :Value))
       (map #(update % :Type (fn [x] (string/replace x "_" " "))))
       drop-invalid-vertical-spatial-domains))

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
