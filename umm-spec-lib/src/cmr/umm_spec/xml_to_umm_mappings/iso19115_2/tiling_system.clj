(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.tiling-system
  (:require
   [clojure.string :as string]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer [value-of]]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.models.umm-collection-models :as umm-c]
   [cmr.umm-spec.spatial-conversion :as spatial-conversion]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.shared-iso-parsing-util :as iso-xml-parsing-util]))

(def tiling-system-xpath
  (str "gmd:extent/gmd:EX_Extent[@id='TilingIdentificationSystem']/gmd:geographicElement/gmd:EX_GeographicDescription/gmd:geographicIdentifier/gmd:MD_Identifier"))

(defn- get-double
  "Parse the input string into a double and return it. Return nil when I can't."
  [str]
  (try
    (Double/parseDouble str)
    (catch NumberFormatException _e nil)
    (catch NullPointerException _e nil)))

(defn- parse-tiling-system-coordinates
  "Returns a map containing :Coordinate1 and :Coordinate2 from an encoded ISO tiling system
  parameter string."
  [tiling-system-str tiling-id-system-name]
  (let [coord-map (iso-xml-parsing-util/convert-iso-description-string-to-map
                   tiling-system-str
                   (re-pattern "c1-min:|c1-max:|c2-min:|c2-max:"))]
    (if (string/includes? tiling-id-system-name "Military Grid Reference System")
      {:Coordinate1 (umm-c/map->TilingCoordinateType
                     (util/remove-nil-keys
                      {:MinimumValue (:c1-min coord-map)
                       :MaximumValue (:c1-max coord-map)}))
       :Coordinate2 (umm-c/map->TilingCoordinateType
                     (util/remove-nil-keys
                      {:MinimumValue (:c2-min coord-map)
                       :MaximumValue (:c2-max coord-map)}))}
      {:Coordinate1 (umm-c/map->TilingCoordinateNumericType
                     (util/remove-nil-keys
                      {:MinimumValue (get-double (:c1-min coord-map))
                       :MaximumValue (get-double (:c1-max coord-map))}))
       :Coordinate2 (umm-c/map->TilingCoordinateNumericType
                     (util/remove-nil-keys
                      {:MinimumValue (get-double (:c2-min coord-map))
                       :MaximumValue (get-double (:c2-max coord-map))}))})))

(defn parse-tiling-system
  [md-data-id-el]
  (for [tiling-system-el (select md-data-id-el tiling-system-xpath)]
    (let [code-string (value-of tiling-system-el "gmd:code/gco:CharacterString")
          description-string (value-of tiling-system-el "gmd:description/gco:CharacterString")
          tiling-id-system-name (spatial-conversion/translate-tile-id-system-name description-string)]
      (when tiling-id-system-name
       (merge
        {:TilingIdentificationSystemName tiling-id-system-name}
        (parse-tiling-system-coordinates code-string tiling-id-system-name))))))
