(ns cmr.umm.iso-smap.collection.spatial
  "Contains functions for parsing and generating the ISO SMAP spatial"
  (:require [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.iso-smap.spatial :as spatial]))

(defn xml-elem->SpatialCoverage
  "Returns a UMM SpatialCoverage from a parsed XML structure"
  [xml-struct]
  ;; SMAP ISO only support BoundingBox spatial geometry
  (let [spatial-elems (cx/elements-at-path
                        xml-struct
                        [:seriesMetadata :MI_Metadata :identificationInfo :MD_DataIdentification
                         :extent :EX_Extent :geographicElement])
        geometries (flatten (map spatial/decode spatial-elems))]
    (when (seq geometries)
      (c/map->SpatialCoverage
       {:granule-spatial-representation :geodetic
        :spatial-representation :geodetic
        :geometries geometries}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generators

(defn generate-spatial
  "Generates XML elements from SpatialCoverage record."
  [{:keys [geometries]}]
  (map spatial/encode
       (filter #(instance? cmr.spatial.mbr.Mbr %)
               geometries)))
