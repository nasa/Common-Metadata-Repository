(ns cmr.umm-spec.umm-to-xml-mappings.iso-shared.quality
  (:require
   [clojure.string :as string]
   [cmr.umm-spec.util :as su :refer [char-string]]))

(defn generate-quality
  "For UMM-C quality this function generates the subfields concatenated together
  into a string. Each element is preceeded by the element name."
  [c]
  (when-let [quality (:Quality c)]
    (let [{:keys [Summary QualityContentDetails]} quality
          summary-part (str "Summary: " Summary " ")
          ;; Map explicitly to maintain a reliable layout order
          details-order [:Strengths :Limitations :KnownIssues :Other]
          details-parts (for [k details-order
                              :let [v (get QualityContentDetails k)]
                              :when (seq v)]
                          (str (name k) ": " v " "))
          full-text (string/trim (apply str summary-part details-parts))]
      [:gmd:report
       [:gmd:DQ_QuantitativeAttributeAccuracy
        [:gmd:evaluationMethodDescription
         (char-string full-text)]
        [:gmd:result {:gco:nilReason "missing"}]]])))
