(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.tiling-system
  (:require [cmr.umm-spec.simple-xpath :refer [select]]
            [cmr.umm-spec.xml.parse :refer :all]))

(def tiling-system-xpath
  (str "gmd:extent/gmd:EX_Extent"
       "/gmd:geographicElement/gmd:EX_GeographicDescription"
       "/gmd:geographicIdentifier/gmd:MD_Identifier"))

(defn parse-tiling-system-coordinates
  "Returns a map containing :Coordinate1 and :Coordinate2 from an encoded ISO tiling system
  parameter string."
  [tiling-system-str]
  (let [[c1 c2] (for [[_ min-str max-str] (re-seq #"(-?\d+\.?\d*)[-,]?(-?\d+\.?\d*)?"
                                                  tiling-system-str)]
                  {:MinimumValue (Double. min-str)
                   :MaximumValue (when max-str (Double. max-str))})]
    {:Coordinate1 c1
     :Coordinate2 c2}))

(defn parse-tiling-system
  [md-data-id-el]
  (when-let [tiling-system-el (first (select md-data-id-el tiling-system-xpath))]
    (let [code-string (value-of tiling-system-el "gmd:code/gco:CharacterString")
          description-string (value-of tiling-system-el "gmd:description/gco:CharacterString")]
      (merge
       {:TilingIdentificationSystemName (or description-string "")}
       (parse-tiling-system-coordinates code-string)))))
