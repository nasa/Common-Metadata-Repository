(ns cmr.umm-spec.umm-g.data-granule
  "Contains functions for parsing UMM-G JSON DataGranule into umm-lib granule model
   DataGranule and generating UMM-G JSON DataGranule from umm-lib granule model DataGranule."
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.umm-spec.util :as util]
   [cmr.umm.umm-granule :as g])
  (:import cmr.umm.umm_granule.UmmGranule))

(def umm-lib-day-night->umm-g-day-night
  "Defines the day night flag mapping between umm-lib and UMM-G models."
  {"DAY" "Day"
   "NIGHT" "Night"
   "BOTH" "Both"
   "UNSPECIFIED" "Unspecified"})

(def umm-g-day-night->umm-lib-day-night
  "Defines the day night flag mapping between UMM-G and umm-lib models."
  (set/map-invert umm-lib-day-night->umm-g-day-night))

(defn- umm-g-file->File
  "Return the umm-lib granule model for a Schema file field"
  [file]
  (when file
    (g/map->File
     {:name (:Name file)
      :size-in-bytes (:SizeInBytes file)
      :size (:Size file)
      :size-unit (:SizeUnit file)
      :format (if (nil? (:Format file)) "Not provided" (:Format file))
      :format-type (:FormatType file)
      :mime-type (:MimeType file)
      :checksum (when-let [checksum (get file :Checksum)]
                  (g/map->Checksum {:value (:Value checksum)
                                    :algorithm (:Algorithm checksum)}))})))

(defn- umm-g-files->Files
  "Return the umm-lib granule model for a list of Schema File fields"
  [files]
  (when files (map umm-g-file->File files)))

(defn umm-g-data-granule->DataGranule
  "Returns the umm-lib granule model DataGranule from the given UMM-G DataGranule."
  [data-granule]
  (when data-granule
    (g/map->DataGranule
      (let [{:keys [Identifiers DayNightFlag ProductionDateTime
                    ArchiveAndDistributionInformation]} data-granule
            first-arch-dist-info (first ArchiveAndDistributionInformation)]
        {:day-night (get umm-g-day-night->umm-lib-day-night DayNightFlag "UNSPECIFIED")
         :producer-gran-id (->> Identifiers
                                (some #(when (= "ProducerGranuleId" (:IdentifierType %)) %))
                                :Identifier)
         :crid-ids (->> Identifiers
                        (filter #(= "CRID" (:IdentifierType %)))
                        (map :Identifier)
                        seq)
         :feature-ids (->> Identifiers
                           (filter #(= "FeatureId" (:IdentifierType %)))
                           (map :Identifier)
                           seq)
         :production-date-time ProductionDateTime
         :size (get (first ArchiveAndDistributionInformation) :Size)
         :size-unit (let [SizeUnit (get (first ArchiveAndDistributionInformation) :SizeUnit)]
                      (when (not= "NA" SizeUnit)
                        SizeUnit))
         :files (umm-g-files->Files (:Files first-arch-dist-info))
         :format (get first-arch-dist-info :Format)
         :size-in-bytes (get first-arch-dist-info :SizeInBytes)
         :checksum (when-let [checksum (get first-arch-dist-info :Checksum)]
                     (g/map->Checksum
                       {:value (:Value checksum)
                        :algorithm (:Algorithm checksum)}))}))))

(defn DataGranule->umm-g-data-granule
  "Returns the UMM-G DataGranule from the given umm-lib granule model DataGranule."
  [data-granule]
  (when data-granule
    (let [{:keys [producer-gran-id day-night crid-ids feature-ids
                  production-date-time size size-unit size-in-bytes checksum]} data-granule]
      {:DayNightFlag (get umm-lib-day-night->umm-g-day-night day-night "Unspecified")
       :Identifiers (let [producer-gran-id (when producer-gran-id
                                             [{:Identifier producer-gran-id
                                               :IdentifierType "ProducerGranuleId"}])
                          crid-ids (map #(assoc {:IdentifierType "CRID"} :Identifier %)
                                        (distinct crid-ids))
                          feature-ids (map #(assoc {:IdentifierType "FeatureId"} :Identifier %)
                                           (distinct feature-ids))]
                       (seq (concat producer-gran-id crid-ids feature-ids)))
       :ProductionDateTime production-date-time
       :ArchiveAndDistributionInformation [{:Name util/not-provided
                                            :Size size
                                            :SizeInBytes size-in-bytes
                                            :Checksum (when checksum
                                                        {:Value (:value checksum)
                                                         :Algorithm (:algorithm checksum)})
                                            :SizeUnit (if size-unit
                                                        size-unit
                                                        "NA")}]})))
