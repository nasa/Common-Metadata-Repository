(ns cmr.umm.echo10.collection.two-d-coordinate-system
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]))

(defn xml-elem->TwoDCoordinateSystem
  [two-d-elem]
  (let [name (cx/string-at-path two-d-elem [:TwoDCoordinateSystemName])]
    (c/map->TwoDCoordinateSystem {:name name})))

(defn xml-elem->TwoDCoordinateSystems
  [collection-element]
  (seq (map xml-elem->TwoDCoordinateSystem
                    (cx/elements-at-path
                      collection-element
                      [:TwoDCoordinateSystems :TwoDCoordinateSystem]))))

(defn generate-two-ds
  [two-d-coordinate-systems]
  (when-not (empty? two-d-coordinate-systems)
    (x/element
      :TwoDCoordinateSystems {}
      (for [two-d two-d-coordinate-systems]
        (let [{:keys [name]} two-d]
          (x/element :TwoDCoordinateSystem {}
                     (x/element :TwoDCoordinateSystemName {} name)
                     ;; Hard coded Coordinate1 and Coordinate2 for now to get through the xml validation
                     ;; should replace this will real code when adding the rest fields of TwoDCoordinateSystem
                     (x/element :Coordinate1 {}
                                (x/element :MinimumValue {} 0)
                                (x/element :MaximumValue {} 10))
                     (x/element :Coordinate2 {}
                                (x/element :MinimumValue {} 0)
                                (x/element :MaximumValue {} 10))))))))
