(ns cmr.umm-spec.migration.distributions-migration
  "Contains helper functions for migrating between different versions of UMM contact information"
  (:require [cmr.umm-spec.util :as u]))

(def distribution-format-type "Native")

(defn migrate-up-to-1_13
  "Migrates Distributions from 1.12 to 1.13"
  [c]
  (let [archive-and-distribution-information
        (for [distribution (:Distributions c)
              :let [media (:DistributionMedia distribution)
                    [size unit] (->> (:Sizes distribution)
                                     (map #((comp vals select-keys) % [:Size :Unit]))
                                     first
                                     vec)
                    format (:DistributionFormat distribution)
                    fees (:Fees distribution)]]
          {:FileDistributionInformation {:Media media
                                         :AverageFileSize size :AverageFileSizeUnit unit
                                         :Format format
                                         :FormatType distribution-format-type
                                         :Fees fees}})]
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
        (for [distribution (:ArchiveAndDistributionInformation c)
              :let [media (get-in distribution [:FileDistributionInformation :Media])
                    [size unit] (-> (:FileDistributionInformation distribution)
                                    (comp vals select-keys) [:AverageFileSize :AverageFileSizeUnit]
                                    vec)
                    format (get-in distribution [:FileDistributionInformation :Format])
                    fees (get-in distribution [:FileDistributionInformation :Fees])]]
          {:DistributionMedia media
           :Sizes [{:Size size :Unit unit}]
           :DistributionFormat format
           :Fees fees})]
    (if (empty? distributions)
      (-> c
          (dissoc :ArchiveAndDistributionInformation))
      (-> c
          (dissoc :ArchiveAndDistributionInformation)
          (assoc :Distributions distributions)))))
