(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2.tiling-system
  "Functions for generating ISO XML tiling system elements."
  (:require [cmr.umm-spec.xml.gen :refer :all]))

(defn- tiling-system-coding-params
  "Returns ISO tiling system encoding string parameters (coordinate 1 prefix, coordinate 2 prefix,
  and range separator) for given tiling system map. The parameters are used by tiling-system-string."
  [tiling-system]
  (condp #(.contains %2 %1) (:TilingIdentificationSystemName tiling-system)
    "CALIPSO" ["o" "p" ","]
    "MISR"    ["p" "b" "-"]
    "MODIS"   ["h" "v" "-"]
    "WRS"     ["p" "r" "-"]
    ["x" "y" "-"]))

(defn- tiling-system-string
  "Returns an encoded ISO tiling system coordinate string from the given UMM tiling system. The
  coordinate 1 and coordinate 2 prefixes and separator may be specified, or else they will be looked
  up based on the tiling system name."
  ([tiling-system p1 p2 sep]
   (let [{{c1-min :MinimumValue
           c1-max :MaximumValue} :Coordinate1
           {c2-min :MinimumValue
            c2-max :MaximumValue} :Coordinate2} tiling-system]
     (str p1 c1-min
          (when c1-max
            (str sep c1-max))
          p2 c2-min
          (when c2-max
            (str sep c2-max)))))
  ([tiling-system]
   (apply tiling-system-string tiling-system (tiling-system-coding-params tiling-system))))

(defn tiling-system-elements
  [c]
  (when-let [tiling-system (:TilingIdentificationSystem c)]
    [:gmd:geographicElement
     [:gmd:EX_GeographicDescription
      [:gmd:geographicIdentifier
       [:gmd:MD_Identifier
        [:gmd:code
         (char-string (tiling-system-string tiling-system))]
        [:gmd:description
         (char-string (:TilingIdentificationSystemName tiling-system))]]]]]))
