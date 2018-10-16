(ns cmr.umm-spec.test.umm-g.expected-util
  "Namespace containing functions for determining expected umm-lib granule records
   used in testing."
  (:require
   [cmr.common.util :as util]
   [cmr.umm-spec.umm-g.measured-parameters :as measured-parameters]
   [cmr.umm.umm-granule :as umm-lib-g]))

(defn- expected-qa-flags
  [qa-flags]
  (let [{:keys [automatic-quality-flag
                automatic-quality-flag-explanation
                operational-quality-flag
                operational-quality-flag-explanation
                science-quality-flag
                science-quality-flag-explanation]}
        qa-flags]
    {:automatic-quality-flag (measured-parameters/sanitize-quality-flag
                              :automatic-quality-flag
                              automatic-quality-flag)
     :automatic-quality-flag-explanation automatic-quality-flag-explanation
     :operational-quality-flag (measured-parameters/sanitize-quality-flag
                                :operational-quality-flag
                                operational-quality-flag)
     :operational-quality-flag-explanation operational-quality-flag-explanation
     :science-quality-flag (measured-parameters/sanitize-quality-flag
                            :science-quality-flag
                            science-quality-flag)
     :science-quality-flag-explanation science-quality-flag-explanation}))

(defn- expected-measured-parameter
  [measured-parameter]
  (update measured-parameter :qa-flags expected-qa-flags))

(defn umm->expected-parsed
  "Modifies the UMM record for testing UMM-G. As the fields are added to UMM-G support for
  parsing and generating in cmr.umm-spec.umm-g.granule, the fields should be taken off the
  excluded list below."
  [gran]
  (-> gran
      (dissoc :spatial-coverage)
      (dissoc :orbit-calculated-spatial-domains)
      (util/update-in-each [:measured-parameters] expected-measured-parameter)
      umm-lib-g/map->UmmGranule))
