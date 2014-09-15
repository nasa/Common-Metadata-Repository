(ns cmr.umm.iso-smap.granule.spatial
  "Contains functions for parsing and generating the ISO SMAP granule spatial"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.granule :as g]
            [cmr.spatial.mbr :as mbr]
            [cmr.umm.iso-smap.helper :as h]
            [cmr.umm.iso-smap.collection.spatial :as cs]))

(defn xml-elem->SpatialCoverage
  "Returns a UMM SpatialCoverage from a parsed XML structure"
  [xml-struct]
  ;; SMAP ISO only support BoundingBox spatial geometry
  (let [spatial-elems (cx/elements-at-path
                        xml-struct
                        [:composedOf :DS_DataSet :has :MI_Metadata :identificationInfo :MD_DataIdentification
                         :extent :EX_Extent :geographicElement :EX_GeographicBoundingBox])]
    (when (seq spatial-elems)
      (let [geometries (map cs/bounding-box-elem->geometry spatial-elems)]
        (g/map->SpatialCoverage {:geometries geometries})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generators

(defn generate-spatial
  "Generates the Spatial element from spatial coverage"
  [spatial-coverage]
  (cs/generate-spatial spatial-coverage))

