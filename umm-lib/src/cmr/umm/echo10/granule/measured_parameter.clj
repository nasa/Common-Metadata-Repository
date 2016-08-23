(ns cmr.umm.echo10.granule.measured-parameter
  "Contains functions for parsing and generating the ECHO10 dialect for measured parameters."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.common.util :as util]
            [cmr.umm.umm-granule :as g]
            [cmr.umm.generator-util :as gu]))

(defn- xml-elem->QAStats
  "Returns a UMM QAStats from a parsed XML structure"
  [elem]
  (when-let [qa-stats-elem (cx/element-at-path elem [:QAStats])]
    (let [missing-data (cx/double-at-path qa-stats-elem [:QAPercentMissingData])
          out-of-bounds-data (cx/double-at-path qa-stats-elem [:QAPercentOutOfBoundsData])
          interpolated-data (cx/double-at-path qa-stats-elem [:QAPercentInterpolatedData])
          cloud-cover (cx/double-at-path qa-stats-elem [:QAPercentCloudCover])
          stats-map (util/remove-nil-keys {:qa-percent-missing-data missing-data
                                           :qa-percent-out-of-bounds-data out-of-bounds-data
                                           :qa-percent-interpolated-data interpolated-data
                                           :qa-percent-cloud-cover cloud-cover})]
      (when (seq stats-map)
        (g/map->QAStats stats-map)))))

(defn- xml-elem->QAFlags
  "Returns a UMM QAFlags from a parsed XML structure"
  [elem]
  (when-let [qa-flags-elem (cx/element-at-path elem [:QAFlags])]
    (let [automatic-quality-flag (cx/string-at-path qa-flags-elem [:AutomaticQualityFlag])
          automatic-quality-flag-explanation (cx/string-at-path
                                               qa-flags-elem [:AutomaticQualityFlagExplanation])
          operational-quality-flag (cx/string-at-path qa-flags-elem [:OperationalQualityFlag])
          operational-quality-flag-explanation (cx/string-at-path
                                                 qa-flags-elem [:OperationalQualityFlagExplanation])
          science-quality-flag (cx/string-at-path qa-flags-elem [:ScienceQualityFlag])
          science-quality-flag-explanation (cx/string-at-path
                                             qa-flags-elem [:ScienceQualityFlagExplanation])
          flags-map (util/remove-nil-keys
                      {:automatic-quality-flag automatic-quality-flag
                       :automatic-quality-flag-explanation automatic-quality-flag-explanation
                       :operational-quality-flag operational-quality-flag
                       :operational-quality-flag-explanation operational-quality-flag-explanation
                       :science-quality-flag science-quality-flag
                       :science-quality-flag-explanation science-quality-flag-explanation})]
      (when (seq flags-map)
        (g/map->QAFlags flags-map)))))

(defn- xml-elem->MeasuredParameter
  "Returns a UMM MeasuredParameter from a parsed XML structure"
  [elem]
  (let [parm-name (cx/string-at-path elem [:ParameterName])
        qa-stats (xml-elem->QAStats elem)
        qa-flags (xml-elem->QAFlags elem)]
    (g/map->MeasuredParameter {:parameter-name parm-name
                               :qa-stats qa-stats
                               :qa-flags qa-flags})))

(defn xml-elem->MeasuredParameters
  "Returns a list of UMM MeasuredParameters from a parsed Granule Content XML structure"
  [granule-element]
  (seq (map xml-elem->MeasuredParameter
            (cx/elements-at-path
              granule-element
              [:MeasuredParameters :MeasuredParameter]))))

(defn- generate-qa-stats
  [qa-stats]
  (when-let [{:keys [qa-percent-missing-data qa-percent-out-of-bounds-data
                     qa-percent-interpolated-data qa-percent-cloud-cover]} qa-stats]
    (x/element :QAStats {}
               (gu/optional-elem :QAPercentMissingData qa-percent-missing-data)
               (gu/optional-elem :QAPercentOutOfBoundsData qa-percent-out-of-bounds-data)
               (gu/optional-elem :QAPercentInterpolatedData qa-percent-interpolated-data)
               (gu/optional-elem :QAPercentCloudCover qa-percent-cloud-cover))))

(defn- generate-qa-flags
  [qa-flags]
  (when-let [{:keys [automatic-quality-flag
                     automatic-quality-flag-explanation
                     operational-quality-flag
                     operational-quality-flag-explanation
                     science-quality-flag
                     science-quality-flag-explanation]} qa-flags]
    (x/element :QAFlags {}
               (gu/optional-elem :AutomaticQualityFlag automatic-quality-flag)
               (gu/optional-elem :AutomaticQualityFlagExplanation automatic-quality-flag-explanation)
               (gu/optional-elem :OperationalQualityFlag operational-quality-flag)
               (gu/optional-elem :OperationalQualityFlagExplanation operational-quality-flag-explanation)
               (gu/optional-elem :ScienceQualityFlag science-quality-flag)
               (gu/optional-elem :ScienceQualityFlagExplanation science-quality-flag-explanation))))

(defn generate-measured-parameters
  [measured-params]
  (when (seq measured-params)
    (x/element
      :MeasuredParameters {}
      (for [{:keys [parameter-name qa-stats qa-flags]} measured-params]
        (x/element :MeasuredParameter {}
                   (x/element :ParameterName {} parameter-name)
                   (generate-qa-stats qa-stats)
                   (generate-qa-flags qa-flags))))))

