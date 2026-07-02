(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.quality
  (:require
   [cmr.umm-spec.iso19115-2-util :as iso-util :refer [char-string-value]]
   [cmr.umm-spec.util :as su]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.shared-iso-parsing-util :as parse-util]))

(def quality-pattern
  "Returns the pattern that matches all the related fields in UMM-C Quality"
  (re-pattern "Summary:|Strengths:|Limitations:|KnownIssues:|Other:"))

(defn parse-quality
  "Parses the passed in ISO document and returns the quality UMM-C element
  and sub elements."
  [doc quality-xpath sanitize?]
  (let [quality-string (char-string-value doc quality-xpath)]
    (when (seq quality-string)
      (let [quality-map (parse-util/convert-iso-description-string-to-map quality-string quality-pattern)
            ;; Fall back to the full text string if the explicit 'Summary:' key wasn't split out
            raw-summary (or (:Summary quality-map) quality-string)
            summary (su/truncate raw-summary su/QUALITY_MAX sanitize?)
            ;; Dynamically assemble only the valid populated inner details
            detail-keys [:Strengths :Limitations :KnownIssues :Other]
            details (into {} (for [k detail-keys
                                   :let [v (get quality-map k)]
                                   :when (seq v)]
                               [k v]))]
        (cond-> {:Summary summary}
          (seq details) (assoc :QualityContentDetails details))))))
