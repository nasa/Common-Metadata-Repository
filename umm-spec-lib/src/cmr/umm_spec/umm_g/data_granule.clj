(ns cmr.umm-spec.umm-g.data-granule
  "Contains functions for parsing UMM-G JSON DataGranule into umm-lib granule model
   DataGranule and generating UMM-G JSON DataGranule from umm-lib granule model DataGranule."
  (:require
   [clojure.set :as set]
   [cmr.umm-spec.util :as util]
   [cmr.umm.umm-granule :as g])
  #_{:clj-kondo/ignore [:unused-import]}
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

(def unit-megabytes-map
  "Mapping SizeUnit to megabytes."
  {"KB" (double (/ 1 1024))
   "MB" 1 
   "GB" 1024
   "TB" (* 1024 1024)
   "PB" (* 1024 1024 1024)
   "NA" 0})

(defn- get-file-size-in-megabytes
  "Get either SizeInBytes or Size(and convert it in Bytes)"
  [file-info]
  (let [size-in-bytes (get file-info :SizeInBytes)
        size (get file-info :Size)
        size-unit (get file-info :SizeUnit)
        convert-to-megabytes-factor (get unit-megabytes-map size-unit)]
    (if size-in-bytes
      (double (/ size-in-bytes (* 1024 1024)))
      (if (and size convert-to-megabytes-factor)
        (* size convert-to-megabytes-factor)
        0))))

(defn- add-up-granule-file-sizes
  "Add up all the granule file sizes from ArchiveAndDistributionInformation."
  [ArchiveAndDistributionInformation]
  (let [sizes (map #(get-file-size-in-megabytes %) ArchiveAndDistributionInformation)
        granule_size (apply + sizes)]
    granule_size))

(defn umm-g-data-granule->DataGranule
  "Returns the umm-lib granule model DataGranule from the given UMM-G DataGranule."
  [data-granule]
  (when data-granule
    (g/map->DataGranule
      (let [{:keys [Identifiers DayNightFlag ProductionDateTime
                    ArchiveAndDistributionInformation]} data-granule
            first-arch-dist-info (first ArchiveAndDistributionInformation)
            size (add-up-granule-file-sizes ArchiveAndDistributionInformation)]
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
         :size size 
         :size-unit (when size 
                      "MB")
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
