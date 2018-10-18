(ns cmr.umm-spec.umm-g.data-granule
  "Contains functions for parsing UMM-G JSON DataGranule into umm-lib granule model
   DataGranule and generating UMM-G JSON DataGranule from umm-lib granule model DataGranule."
  (:require
   [cmr.umm.umm-granule :as g]
   [clojure.string :as string]
   [cmr.umm-spec.util :as util])
  (:import cmr.umm.umm_granule.UmmGranule))

(defn umm-g-data-granule->DataGranule
  "Returns the umm-lib granule model DataGranule from the given UMM-G DataGranule."
  [data-granule]
  (when data-granule
    (g/map->DataGranule
      (let [{:keys [Identifiers DayNightFlag ProductionDateTime
                    ArchiveAndDistributionInformation]} data-granule]
        {:day-night (string/upper-case DayNightFlag)
         :producer-gran-id (->> Identifiers
                                (some #(when (= "ProducerGranuleId" (:IdentifierType %)) %))
                                :Identifier)
         :crid-ids (->> Identifiers
                        (filter #(= "CRID" (:IdentifierType %)))
                        (map :Identifier)
                        distinct
                        seq) 
         :feature-ids (->> Identifiers
                        (filter #(= "FeatureId" (:IdentifierType %)))    
                        (map :Identifier)
                        distinct
                        seq)
         :production-date-time ProductionDateTime
         :size (get (first ArchiveAndDistributionInformation) :Size)}))))

(defn DataGranule->umm-g-data-granule
  "Returns the UMM-G DataGranule from the given umm-lib granule model DataGranule."
  [data-granule]
  (when data-granule
    (let [{:keys [producer-gran-id crid-ids feature-ids
                  day-night production-date-time size]} data-granule]
      {:DayNightFlag (string/capitalize day-night)
       :Identifiers (let [producer-gran-id (when producer-gran-id
                                             [{:Identifier producer-gran-id
                                               :IdentifierType "ProducerGranuleId"}])
                          crid-ids (when (seq crid-ids)
                                     (map #(assoc {:IdentifierType "CRID"} :Identifier %) 
                                          (distinct crid-ids)))
                          feature-ids (when (seq feature-ids)
                                        (map #(assoc {:IdentifierType "FeatureId"} :Identifier %) 
                                             (distinct feature-ids)))]
                      (when (or (seq producer-gran-id) (seq crid-ids) (seq feature-ids))
                        (concat producer-gran-id crid-ids feature-ids)))
       :ProductionDateTime production-date-time
       :ArchiveAndDistributionInformation [{:Name util/not-provided
                                            :Size size
                                            :SizeUnit "NA"}]})))
