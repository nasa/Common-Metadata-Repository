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

(defn- preserve-detail-edge-whitespace
  "For ISO quality strings, preserves leading/trailing whitespace on populated
   quality detail fields by restoring raw content from quality-string when
   parsing trim caused data loss.

   For non-terminal fields we remove one trailing separator space that is
   introduced by the ISO writer between key/value pairs."
  [quality-string details]
  (let [indexed-details (->> detail-keys
                             (keep (fn [k]
                                     (let [detail-label (str (name k) ": ")
                                           idx (string/last-index-of quality-string detail-label)]
                                       (when (and (seq (get details k)) (some? idx))
                                         [k idx detail-label]))))
                             (sort-by second))]
    (reduce
     (fn [acc [k idx detail-label]]
       (let [parsed-value (get acc k)
             value-start (+ idx (count detail-label))
             next-label-idx (some->> detail-keys
                                     (map (fn [candidate-k]
                                            (let [candidate-label (str (name candidate-k) ": ")
                                                  candidate-idx (string/index-of quality-string candidate-label value-start)]
                                              (when (some? candidate-idx) candidate-idx))))
                                     (remove nil?)
                                     seq
                                     (apply min))
             raw-value (if (some? next-label-idx)
                         (subs quality-string value-start next-label-idx)
                         (subs quality-string value-start))
             value-without-joiner (if (and (some? next-label-idx)
                                           (string/ends-with? raw-value " "))
                                    (subs raw-value 0 (dec (count raw-value)))
                                    raw-value)]
         (cond
           ;; Terminal field: preserve leading/trailing spaces if trim-only loss occurred.
           (and (nil? next-label-idx)
                (= (string/trim raw-value) parsed-value)
                (not= raw-value parsed-value))
           (assoc acc k raw-value)

           ;; Non-terminal field: preserve leading/trailing content whitespace while
           ;; removing one writer-introduced joiner space.
           (and (some? next-label-idx)
                (= (string/trim raw-value) parsed-value)
                (= (string/trim value-without-joiner) parsed-value)
                (not= value-without-joiner parsed-value))
           (assoc acc k value-without-joiner)

           :else
           acc)))
     details
     indexed-details)))

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
            details (preserve-detail-edge-whitespace quality-string details)]
        (cond-> {:Summary summary}
          (seq details) (assoc :QualityContentDetails details))))))
