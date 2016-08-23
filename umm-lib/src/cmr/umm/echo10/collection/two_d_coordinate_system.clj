(ns cmr.umm.echo10.collection.two-d-coordinate-system
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.common.util :as util]
            [cmr.umm.umm-collection :as c]))

(defn- xml-elem->Coordinate
  [coord-elem]
  (let [min-value (cx/double-at-path coord-elem [:MinimumValue])
        max-value (cx/double-at-path coord-elem [:MaximumValue])
        value-map (util/remove-nil-keys {:min-value min-value
                                         :max-value max-value})]
    (when (seq value-map)
      (c/map->Coordinate value-map))))

(defn- xml-elem->TwoDCoordinateSystem
  [two-d-elem]
  (let [name (cx/string-at-path two-d-elem [:TwoDCoordinateSystemName])
        coord-1-elem (cx/element-at-path two-d-elem [:Coordinate1])
        coord-2-elem (cx/element-at-path two-d-elem [:Coordinate2])]
    (c/map->TwoDCoordinateSystem {:name name
                                  :coordinate-1 (xml-elem->Coordinate coord-1-elem)
                                  :coordinate-2 (xml-elem->Coordinate coord-2-elem)})))

(defn xml-elem->TwoDCoordinateSystems
  [collection-element]
  (seq (map xml-elem->TwoDCoordinateSystem
            (cx/elements-at-path collection-element
                                 [:TwoDCoordinateSystems :TwoDCoordinateSystem]))))


(defn- generate-coordinate
  [coord-key coord]
  (let [{:keys [min-value max-value]} coord]
    (x/element coord-key {}
               (when min-value (x/element :MinimumValue {} min-value))
               (when max-value (x/element :MaximumValue {} max-value)))))

(defn generate-two-ds
  [two-d-coordinate-systems]
  (when (seq two-d-coordinate-systems)
    (x/element
      :TwoDCoordinateSystems {}
      (for [two-d two-d-coordinate-systems]
        (let [{:keys [name coordinate-1 coordinate-2]} two-d]
          (x/element :TwoDCoordinateSystem {}
                     (x/element :TwoDCoordinateSystemName {} name)
                     (generate-coordinate :Coordinate1 coordinate-1)
                     (generate-coordinate :Coordinate2 coordinate-2)))))))
