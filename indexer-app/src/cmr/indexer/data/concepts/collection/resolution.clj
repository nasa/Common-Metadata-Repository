(ns cmr.indexer.data.concepts.collection.resolution
  "Contains functions to parse and convert collection resolution information so that
   the information can be indexed."
  (:require
   [cmr.common.util :refer [remove-nils-empty-maps-seqs convert-to-meters]]))

(defn- get-non-range-data-resolution
  "Extracts and converts the non range horizontal data resolution values to meters.
   Returns a sequence of non nil values."
  [resolution]
  (let [unit (:Unit resolution)]
    (when-not (or (= "Scan Extremes" (:ViewingAngleType resolution))
                  (= "Not provided" unit)
                  (nil? unit))
      (remove-nils-empty-maps-seqs
        [(convert-to-meters (:XDimension resolution) unit)
         (convert-to-meters (:YDimension resolution) unit)]))))

(defn- get-range-data-resolution
  "Extracts and converts the range horizontal data resolution values to meters.
   Returns a sequence of non nil values."
  [resolution]
  (let [unit (:Unit resolution)]
    (when-not (or (= "Scan Extremes" (:ViewingAngleType resolution))
                  (= "Not provided" unit)
                  (nil? unit))
      (remove-nils-empty-maps-seqs
        [(convert-to-meters (:MinimumXDimension resolution) unit)
         (convert-to-meters (:MaximumXDimension resolution) unit)
         (convert-to-meters (:MinimumYDimension resolution) unit)
         (convert-to-meters (:MaximumYDimension resolution) unit)]))))

(defn get-non-range-data-resolutions
  "Extracts and converts a list of non range horizontal data resolutions values to meters.
   Returns a sequence of distinct non nil values."
  [resolutions]
  (distinct (mapcat get-non-range-data-resolution resolutions)))

(defn get-range-data-resolutions
  "Extracts and converts a list of range horizontal data resolutions values to meters.
   Returns a sequence of distinct non nil values."
  [resolutions]
  (distinct (mapcat get-range-data-resolution resolutions)))

(defn get-horizontal-data-resolutions
  "Extracts and converts all of the horizontal data resolutions values to meters.
   Returns a sequence of distinct non nil values."
  [horizontal-data-resolutions]
  (let [point-resolution (when (:PointResolution horizontal-data-resolutions) (quote (0)))
        non-gridded-resolutions (get-non-range-data-resolutions
                                  (:NonGriddedResolutions horizontal-data-resolutions))
        non-gridded-range-resolutions (get-range-data-resolutions
                                        (:NonGriddedRangeResolutions horizontal-data-resolutions))
        gridded-resolutions (get-non-range-data-resolutions
                              (:GriddedResolutions horizontal-data-resolutions))
        gridded-range-resolutions (get-range-data-resolutions
                                    (:GriddedRangeResolutions horizontal-data-resolutions))
        generic-resolutions (get-non-range-data-resolutions
                              (:GenericResolutions horizontal-data-resolutions))]
    (distinct
      (concat point-resolution
              non-gridded-resolutions
              non-gridded-range-resolutions
              gridded-resolutions
              gridded-range-resolutions
              generic-resolutions))))
