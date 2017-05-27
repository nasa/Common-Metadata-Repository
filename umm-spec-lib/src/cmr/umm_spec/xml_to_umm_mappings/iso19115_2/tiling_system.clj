(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.tiling-system
  (:require [cmr.common.xml.simple-xpath :refer [select]]
            [cmr.common.xml.parse :refer :all]
            [cmr.umm-spec.xml-to-umm-mappings.iso-shared.distributions-related-url :as sdru]))

(def tiling-system-xpath
  (str "gmd:extent/gmd:EX_Extent[@id='TilingIdentificationSystem']/gmd:geographicElement/gmd:EX_GeographicDescription/gmd:geographicIdentifier/gmd:MD_Identifier"))

(defn- get-double
  "Parse the input string into a double and return it. Return nil when I can't."
  [str]
  (try
    (Double/parseDouble str)
    (catch NumberFormatException e (when nil))
    (catch NullPointerException e (when nil))))

(defn- parse-tiling-system-coordinates
  "Returns a map containing :Coordinate1 and :Coordinate2 from an encoded ISO tiling system
  parameter string."
  [tiling-system-str]
  (def tiling-system-str tiling-system-str)
  (let [c1-min-index (sdru/get-index-or-nil tiling-system-str "c1-min:")
        c1-max-index (sdru/get-index-or-nil tiling-system-str "c1-max:")
        c2-min-index (sdru/get-index-or-nil tiling-system-str "c2-min:")
        c2-max-index (sdru/get-index-or-nil tiling-system-str "c2-max:")
        end-index (count tiling-system-str)
        c1-min (when c1-min-index
                  (sdru/get-substring tiling-system-str c1-min-index c1-max-index c2-min-index c2-max-index end-index))
        c1-max (when c1-max-index
                  (sdru/get-substring tiling-system-str c1-max-index c2-min-index c2-max-index end-index))
        c2-min (when c2-min-index
                 (sdru/get-substring tiling-system-str c2-min-index c2-max-index end-index))
        c2-max (when c2-max-index
                 (sdru/get-substring tiling-system-str c2-max-index end-index))]
    {:Coordinate1 {:MinimumValue (get-double c1-min)
                   :MaximumValue (get-double c1-max)}
     :Coordinate2 {:MinimumValue (get-double c2-min)
                   :MaximumValue (get-double c2-max)}}))

(defn parse-tiling-system
  [md-data-id-el]
  (for [tiling-system-el (select md-data-id-el tiling-system-xpath)]
    (let [code-string (value-of tiling-system-el "gmd:code/gco:CharacterString")
          description-string (value-of tiling-system-el "gmd:description/gco:CharacterString")]
      (merge
       {:TilingIdentificationSystemName (or description-string "")}
       (parse-tiling-system-coordinates code-string)))))
