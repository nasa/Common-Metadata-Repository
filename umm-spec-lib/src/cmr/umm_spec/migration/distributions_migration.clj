(ns cmr.umm-spec.migration.distributions-migration
  "Contains helper functions for migrating between different versions of UMM contact information"
  (:require
   [cmr.common.util :as util]))

(def distribution-format-type "Native")

(defn migrate-up-to-1_13
  "Migrates Distributions from 1.12 to 1.13"
  [c]
  (let [archive-and-distribution-information
        {:FileDistributionInformation
         (for [distribution (:Distributions c)
               :let [[size unit] (->> (:Sizes distribution)
                                      (map #((comp vals select-keys) % [:Size :Unit]))
                                      first
                                      vec)
                     media (:DistributionMedia distribution)]]
           (util/remove-nil-keys
            {:Media (when media
                      [media])
             :AverageFileSize size :AverageFileSizeUnit unit
             :Format (:DistributionFormat distribution)
             :FormatType distribution-format-type
             :Fees (:Fees distribution)}))}]
    (if (empty? archive-and-distribution-information)
      (-> c
          (dissoc :Distributions))
      (-> c
          (dissoc :Distributions)
          (assoc :ArchiveAndDistributionInformation archive-and-distribution-information)))))

(defn migrate-down-to-1_12
  "Migrates ArchiveAndDistributionInformation from 1.13 to 1.12"
  [c]
  (let [distributions
        (for [distribution (get-in c [:ArchiveAndDistributionInformation :FileDistributionInformation])
              :let [select-values (comp vals select-keys)
                    [size unit] (-> distribution
                                    (select-values [:AverageFileSize :AverageFileSizeUnit])
                                    vec)]]
          (util/remove-nil-keys
            {:DistributionMedia (first (:Media distribution))
             :Sizes [{:Size size :Unit unit}]
             :DistributionFormat (:Format distribution)
             :Fees (:Fees distribution)}))]
    (if (empty? distributions)
      (-> c
          (dissoc :ArchiveAndDistributionInformation))
      (-> c
          (dissoc :ArchiveAndDistributionInformation)
          (assoc :Distributions distributions)))))
