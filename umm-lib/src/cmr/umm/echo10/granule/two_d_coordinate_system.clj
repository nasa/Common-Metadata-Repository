(ns cmr.umm.echo10.granule.two-d-coordinate-system
  "Contains functions for parsing and generating the ECHO10 granule two-d-coordinate-system element."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-granule :as g]))

(defn xml-elem->TwoDCoordinateSystem
  "Returns a UMM TwoDCoordinateSystem from a parsed Granule Content XML structure"
  [granule-element]
  (when-let [two-d-element (cx/element-at-path granule-element [:TwoDCoordinateSystem])]
    (g/map->TwoDCoordinateSystem
      {:name (cx/string-at-path two-d-element [:TwoDCoordinateSystemName])
       :start-coordinate-1 (cx/double-at-path two-d-element [:StartCoordinate1])
       :end-coordinate-1 (cx/double-at-path two-d-element [:EndCoordinate1])
       :start-coordinate-2 (cx/double-at-path two-d-element [:StartCoordinate2])
       :end-coordinate-2 (cx/double-at-path two-d-element [:EndCoordinate2])})))

(defn generate-two-d-coordinate-system
  "Generates the two d coordinate system element of ECHO10 XML from a UMM Granule two d record."
  [two-d-coordinate-system]
  (when two-d-coordinate-system
    (let [{:keys [name start-coordinate-1 end-coordinate-1
                  start-coordinate-2 end-coordinate-2]} two-d-coordinate-system]
      (x/element :TwoDCoordinateSystem {}
                 (x/element :StartCoordinate1 {} start-coordinate-1)
                 (when end-coordinate-1 (x/element :EndCoordinate1 {} end-coordinate-1))
                 (x/element :StartCoordinate2 {} start-coordinate-2)
                 (when end-coordinate-2 (x/element :EndCoordinate2 {} end-coordinate-2))
                 (x/element :TwoDCoordinateSystemName {} name)))))

