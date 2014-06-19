(ns cmr.umm.dif.collection.spatial-coverage
  "Provide functions to parse and generate DIF spatial-coverage info, it is mapped to an Extended_Metadata element."
  (:require [cmr.umm.collection :as c]
            [cmr.umm.dif.collection.extended-metadata :as em]
            [camel-snake-kebab :as csk]))

(def SPATIAL_COVERAGE_EXTERNAL_META_NAME
  "GranuleSpatialRepresentation")

(defn xml-elem->SpatialCoverage
  "Returns a UMM SpatialCoverage from a parsed Collection XML structure"
  [xml-struct]
  ;; DIF: Extended_Metadata.Name=GranuleSpatialRepresention
  (when-let [ems (em/xml-elem->extended-metadatas xml-struct)]
    (let [rep (filter #(= SPATIAL_COVERAGE_EXTERNAL_META_NAME (:name %)) ems)]
      (when-not (empty? rep)
        (c/map->SpatialCoverage
          {:granule-spatial-representation (csk/->kebab-case-keyword (:value (first rep)))})))))

(defn SpatialCoverage->extended-metadata
  "Returns an extended-metadata map for the given SpatialCoverage"
  [spatial-coverage]
  {:name SPATIAL_COVERAGE_EXTERNAL_META_NAME
   :value (csk/->SNAKE_CASE_STRING (:granule-spatial-representation spatial-coverage))})
