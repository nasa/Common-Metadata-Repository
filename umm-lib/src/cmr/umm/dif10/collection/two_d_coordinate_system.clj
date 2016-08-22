(ns cmr.umm.dif10.collection.two-d-coordinate-system
  "Contains functions for convert two d coordinate systems to and parsing from DIF10 XML."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.common.util :as util]
            [cmr.umm.umm-collection :as c]))

(defn- xml-elem->Coordinate
  "Returns a UMM Coordinate from a parsed Coordinate XML structure"
  [coord-elem]
  (let [min-value (cx/double-at-path coord-elem [:Minimum_Value])
        max-value (cx/double-at-path coord-elem [:Maximum_Value])
        value-map (util/remove-nil-keys {:min-value min-value
                                         :max-value max-value})]
    (when (seq value-map)
      (c/map->Coordinate value-map))))

(defn- xml-elem->TwoDCoordinateSystem
  "Returns a UMM TwoDCoordinateSystem from a parsed TwoDCoordinateSystem XML structure"
  [two-d-elem]
  (let [name (cx/string-at-path two-d-elem [:TwoD_Coordinate_System_Name])
        coord-1-elem (cx/element-at-path two-d-elem [:Coordinate1])
        coord-2-elem (cx/element-at-path two-d-elem [:Coordinate2])]
    (c/map->TwoDCoordinateSystem {:name name
                                  :coordinate-1 (xml-elem->Coordinate coord-1-elem)
                                  :coordinate-2 (xml-elem->Coordinate coord-2-elem)})))

(defn xml-elem->TwoDCoordinateSystems
  "Returns UMM TwoDCoordinateSystems from a parsed Collection XML structure"
  [collection-element]
  (seq (map xml-elem->TwoDCoordinateSystem
            (cx/elements-at-path collection-element
                                 [:Spatial_Coverage :Spatial_Info :TwoD_Coordinate_System]))))


(defn- generate-coordinate
  "Returns coordinate xml structure for the given coordinate"
  [coord-key coord]
  (let [{:keys [min-value max-value]} coord]
    (x/element coord-key {}
               (when min-value (x/element :Minimum_Value {} min-value))
               (when max-value (x/element :Maximum_Value {} max-value)))))

(defn generate-two-ds
  "Returns DIF10 Spatial_Info element for the given two d coordinate systems"
  [two-d-coordinate-systems]
  (when (seq two-d-coordinate-systems)
    (x/element
      :Spatial_Info {}
      ;; Spatial_Coverage_Type is always Horizontal
      (x/element :Spatial_Coverage_Type {} "Horizontal")
      (for [two-d two-d-coordinate-systems]
        (let [{:keys [name coordinate-1 coordinate-2]} two-d]
          (x/element :TwoD_Coordinate_System {}
                     (x/element :TwoD_Coordinate_System_Name {} name)
                     (generate-coordinate :Coordinate1 coordinate-1)
                     (generate-coordinate :Coordinate2 coordinate-2)))))))
