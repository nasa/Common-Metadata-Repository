(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.quality
  (:require
   [clojure.string :as string]
   [cmr.umm-spec.iso19115-2-util :as iso-util :refer [char-string-value]]
   [cmr.umm-spec.util :as su]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.shared-iso-parsing-util :as parse-util]))

(def quality-pattern
  "Returns the pattern that matches all the related fields in UMM-C Quality"
  (re-pattern "Summary:|Strengths:|Limitations:|KnownIssues:|Other:"))

(def ^:private detail-keys
  [:Strengths :Limitations :KnownIssues :Other])

(defn- preserve-last-detail-trailing-whitespace
  "For ISO quality strings, preserves trailing whitespace on the last populated
  quality detail field (if present) by restoring its raw value from the source
  quality-string. This avoids data loss caused by generic description parsing trim."
  [quality-string details]
  (let [indexed-details (keep (fn [k]
                                (let [detail-label (str (name k) ": ")
                                      idx (string/last-index-of quality-string detail-label)]
                                  (when (and (seq (get details k)) (some? idx))
                                    [k idx detail-label])))
                              detail-keys)]
    (if-let [[last-key idx detail-label] (last (sort-by second indexed-details))]
      (let [value-start (+ idx (count detail-label))
            next-label-idx (some->> detail-keys
                                    (map (fn [k]
                                           (let [candidate-label (str (name k) ": ")
                                                 candidate-idx (string/index-of quality-string candidate-label value-start)]
                                             (when (some? candidate-idx) candidate-idx))))
                                    (remove nil?)
                                    seq
                                    (apply min))
            raw-value (if (some? next-label-idx)
                        (subs quality-string value-start next-label-idx)
                        (subs quality-string value-start))]
        ;; Only override when parsing trim likely altered value.
        ;; Also only preserve if this field is the terminal field in the source
        ;; quality string (no next detail label), so separator spaces are not
        ;; treated as meaningful content.
        (if (and (nil? next-label-idx)
                 (= (string/trimr raw-value) (get details last-key)))
          (assoc details last-key raw-value)
          details))
      details)))

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
            details (into {} (for [k detail-keys
                                   :let [v (get quality-map k)]
                                   :when (seq v)]
                               [k v]))
            details (preserve-last-detail-trailing-whitespace quality-string details)]
        (cond-> {:Summary summary}
          (seq details) (assoc :QualityContentDetails details))))))
