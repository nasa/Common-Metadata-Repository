(ns cmr.umm-spec.umm-g.measured-parameters
  "Contains functions for parsing and generating the UMM-G dialect for measured parameters."
  (:require
   [cmr.umm.umm-granule :as g]
   [cmr.common.util :as util])
  (:import cmr.umm.umm_granule.UmmGranule))

(defn- umm-g-qa-stats->QAStats
  "Returns umm-lib QAStats from given UMM-G QAStats."
  [qa-stats]
  (when-not (empty? (util/remove-nil-keys qa-stats))
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
  (when-not (empty? (util/remove-nil-keys qa-stats))
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
  (when-not (empty? (util/remove-nil-keys qa-flags))
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

(def enum-default-value
  "Undetermined")

(defmulti flag-enum
  (fn [flag-type]
    flag-type))

(defmethod flag-enum :automatic-quality-flag
  [flag-type]
  ["Passed" "Failed" "Suspect" enum-default-value])

(defmethod flag-enum :operational-quality-flag
  [flag-type]
  ["Passed" "Failed" "Being Investigated" "Not Investigated"
   "Inferred Passed" "Inferred Failed" "Suspect" enum-default-value])

(defmethod flag-enum :science-quality-flag
  [flag-type]
  ["Passed" "Failed" "Being Investigated" "Not Investigated"
   "Inferred Passed" "Inferred Failed" "Suspect" "Hold" enum-default-value])

(defn sanitize-quality-flag
  "Sanitizes quality flag based on quality type.  Maps to enum values in json schema.  If
   those enum values change, we need to have it reflected here as well.  If the flag value is nil,
   we do not use the default value, otherwise non enum values are mapped to the default."
  [flag-type flag-value]
  (when flag-value
    (if (some #(= % flag-value)
              (flag-enum flag-type))
      flag-value
      enum-default-value)))

(defn- QAFlags->umm-g-qa-flags
  "Returns umm-lib QAFlags from given UMM-G QAFlags."
  [qa-flags]
  (when-not (empty? (util/remove-nil-keys qa-flags))
    (let [{:keys [automatic-quality-flag
                  automatic-quality-flag-explanation
                  operational-quality-flag
                  operational-quality-flag-explanation
                  science-quality-flag
                  science-quality-flag-explanation]}
          qa-flags]
      {:AutomaticQualityFlag (sanitize-quality-flag :automatic-quality-flag
                                                    automatic-quality-flag)
       :AutomaticQualityFlagExplanation automatic-quality-flag-explanation
       :OperationalQualityFlag (sanitize-quality-flag :operational-quality-flag
                                                      operational-quality-flag)
       :OperationalQualityFlagExplanation operational-quality-flag-explanation
       :ScienceQualityFlag (sanitize-quality-flag :science-quality-flag
                                                  science-quality-flag)
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
