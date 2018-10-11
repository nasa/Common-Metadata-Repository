(ns cmr.umm-spec.umm-g.measured-parameters
  "Contains functions for parsing and generating the UMM-G dialect for measured parameters."
  (:require
   [cmr.umm.umm-granule :as g])
  (:import cmr.umm.umm_granule.UmmGranule))

(defn- umm-g-qa-stats->QAStats
  "Returns umm-lib QAStats from given UMM-G QAStats."
  [qa-stats]
  (when qa-stats
    (let [{:keys [QAPercentMissingData
                  QAPercentOutOfBoundsData
                  QAPercentInterpolatedData
                  QAPercentCloudCover]} qa-stats]
      (g/map->QAStats
       {:qa-percent-missing-data QAPercentMissingData
        :qa-percent-out-of-bounds-data QAPercentOutOfBoundsData
        :qa-percent-interpolated-data QAPercentInterpolatedData
        :qa-percent-cloud-cover QAPercentCloudCover}))))

(defn- QAStats->umm-g-qa-stats
  "Returns UMM-G QAStats from given umm-lib QAStats."
  [qa-stats]
  (when qa-stats
    (let [{:keys [qa-percent-missing-data
                  qa-percent-out-of-bounds-data
                  qa-percent-interpolated-data
                  qa-percent-cloud-cover]}
          qa-stats]
      {:QAPercentMissingData qa-percent-missing-data
       :QAPercentOutOfBoundsData qa-percent-out-of-bounds-data
       :QAPercentInterpolatedData qa-percent-interpolated-data
       :QAPercentCloudCover qa-percent-cloud-cover})))

(defn- umm-g-qa-flags->QAFlags
  "Returns UMM-G QAFlags from given umm-lib QAFlags."
  [qa-flags]
  (when qa-flags
    (let [{:keys [AutomaticQualityFlag
                  AutomaticQualityFlagExplanation
                  OperationalQualityFlag
                  OperationalQualityFlagExplanation
                  ScienceQualityFlag
                  ScienceQualityFlagExplanation]}

          qa-flags]
      (g/map->QAFlags
       {:automatic-quality-flag AutomaticQualityFlag
        :automatic-quality-flag-explanation AutomaticQualityFlagExplanation
        :operational-quality-flag OperationalQualityFlag
        :operational-quality-flag-explanation OperationalQualityFlagExplanation
        :science-quality-flag ScienceQualityFlag
        :science-quality-flag-explanation ScienceQualityFlagExplanation}))))

(defn- QAFlags->umm-g-qa-flags
  "Returns umm-lib QAFlags from given UMM-G QAFlags."
  [qa-flags]
  (when qa-flags
    (let [{:keys [automatic-quality-flag
                  automatic-quality-flag-explanation
                  operational-quality-flag
                  operational-quality-flag-explanation
                  science-quality-flag
                  science-quality-flag-explanation]}
          qa-flags]
      {:AutomaticQualityFlag automatic-quality-flag
       :AutomaticQualityFlagExplanation automatic-quality-flag-explanation
       :OperationalQualityFlag operational-quality-flag
       :OperationalQualityFlagExplanation operational-quality-flag-explanation
       :ScienceQualityFlag science-quality-flag
       :ScienceQualityFlagExplanation science-quality-flag-explanation})))

(defn- umm-g-measured-parameter->MeasuredParmeter
  "Returns umm-lib MeasuredParameter from given UMM-G MeasuredParameter."
  [measured-parameter]
  (let [{:keys [ParameterName QAStats QAFlags]} measured-parameter]
    (g/map->MeasuredParameter
     {:parameter-name ParameterName
      :qa-stats (umm-g-qa-stats->QAStats QAStats)
      :qa-flags (umm-g-qa-flags->QAFlags QAFlags)})))

(defn- MeasuredParmeter->umm-g-measured-parameter
  "Returns UMM-G MeasuredParameter from given umm-lib MeasuredParameter."
  [measured-parameter]
  (let [{:keys [parameter-name qa-stats qa-flags]} measured-parameter]
    (g/map->MeasuredParameter
     {:ParameterName parameter-name
      :QAStats (QAStats->umm-g-qa-stats qa-stats)
      :QAFlags (QAFlags->umm-g-qa-flags qa-flags)})))

(defn umm-g-measured-parameters->MeasuredParameters
  "Returns umm-lib MeasuredParameters from given UMM-G MeasuredParameters."
  [measured-parameters]
  (when (seq measured-parameters)
    (map umm-g-measured-parameter->MeasuredParmeter measured-parameters)))

(defn MeasuredParameters->umm-g-measured-parameters
  "Returns UMM-G MeasuredParameters from given umm-lib MeasuredParameters."
  [measured-parameters]
  (when (seq measured-parameters)
    (map MeasuredParmeter->umm-g-measured-parameter measured-parameters)))
