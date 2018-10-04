(ns cmr.umm-spec.umm-g.data-granule
  "Contains functions for parsing UMM-G JSON DataGranule into umm-lib granule model
   DataGranule and generating UMM-G JSON DataGranule from umm-lib granule model DataGranule."
  (:require
   [cmr.umm.umm-granule :as g])
  (:import cmr.umm.umm_granule.UmmGranule))

(defn umm-g-data-granule->DataGranule
  "Returns the umm-lib granule model DataGranule from the given UMM-G DataGranule."
  [data-granule]
  (when (seq data-granule)
    (g/map->DataGranule
      (let [{:keys [Identifiers DayNightFlag ProductionDateTime ArchiveAndDistributionInformation]} data-granule]
        {:day-night DayNightFlag
         :producer-gran-id (->> Identifiers
                                (filter #(= "ProducerGranuleId" (:IdentifierType %)))
                                first
                                :Identifier)
         :production-date-time ProductionDateTime
         :size (get (first ArchiveAndDistributionInformation) :Size)}))))

(defn DataGranule->umm-g-data-granule
  "Returns the UMM-G DataGranule from the given umm-lib granule model DataGranule."
  [data-granule]
  (when (seq data-granule)
    (let [{:keys [producer-gran-id day-night production-date-time size]} data-granule]
      {:DayNightFlag day-night
       :Identifiers (when producer-gran-id
                      [{:Identifier producer-gran-id
                        :IdentifierType "ProducerGranuleId"}])
       :ProductionDateTime production-date-time
       :ArchiveAndDistributionInformation [{:Name "Not provided"
                                            :Size size
                                            :SizeUnit "MB"}]})))
