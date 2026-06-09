(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.quality
  (:require
   [clojure.string :as string]
   [cmr.common.xml.parse :refer [value-of]]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.util :as su]))

(defn parse-quality-summary-only
  "Extracts Summary and reconstructs QualityContentDetails from the triple-pipe 
   encoded string to satisfy both UMM v1.18.6 and round-trip assertions."
  [doc quality-xpath-or-node]
  (let [quality-node (if (string? quality-xpath-or-node)
                       (first (select doc quality-xpath-or-node))
                       quality-xpath-or-node)]
    (when-let [raw-string (value-of quality-node "gmd:report/gmd:DQ_QuantitativeAttributeAccuracy/gmd:evaluationMethodDescription/gco:CharacterString")]
      (if (string/includes? raw-string "|||")
        ;; Split on the main summary partition vs granular fields
        (let [parts (string/split raw-string #"\|\|\|")
              summary-part (first parts)
              ;; Gather any subsequent reports to restore details arrays accurately
              report-nodes (select quality-node "gmd:report/gmd:DQ_QuantitativeAttributeAccuracy")
              ;; Filter out the first composite string to isolate specific detail blocks
              detail-nodes (rest report-nodes)
              details (keep (fn [report]
                              (let [detail-str (value-of report "gmd:evaluationMethodDescription/gco:CharacterString")]
                                (when (string/includes? detail-str "|||")
                                  (let [d-parts (string/split detail-str #"\|\|\|")]
                                    {:TypeOfContent (nth d-parts 0 "Other")
                                     :ContentDescription (nth d-parts 1 su/not-provided)}))))
                            detail-nodes)]
          (cond-> {}
            (not (string/blank? summary-part)) (assoc :Summary summary-part)
            (seq details) (assoc :QualityContentDetails (vec details))))

        ;; Fallback if no triple-pipe delimiter is found
        (when-not (string/blank? raw-string)
          {:Summary raw-string})))))