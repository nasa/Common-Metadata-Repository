(ns cmr.umm-spec.umm-to-xml-mappings.iso-shared.quality
  (:require [clojure.string :as string]))

(defn generate-quality
  "Generates an ordered sequence of schema-valid ISO XML elements for a given UMM Quality record.
   Guarantees no empty self-closing tags are emitted if data fields are missing."
  [c]
  (let [quality (:Quality c)
        summary (:Summary quality)
        details (:QualityContentDetails quality)]
    (concat
     ;; 1. Consolidated Quantitative Report Block (Only if summary or details exist)
     (when (or (not (string/blank? summary)) (seq details))
       [[:gmd:report
         [:gmd:DQ_QuantitativeAttributeAccuracy
          [:gmd:evaluationMethodDescription
           [:gco:CharacterString
            (str (or summary "") "|||"
                 (string/join "; " (map #(str (:TypeOfContent %) ": " (:ContentDescription %)) details)))]]
          [:gmd:result {:gco:nilReason "missing"}]]]])

     ;; 2. Individual Detail Report Blocks
     (keep (fn [detail]
             (when (not (string/blank? (:ContentDescription detail)))
               [:gmd:report
                [:gmd:DQ_QuantitativeAttributeAccuracy
                 [:gmd:evaluationMethodDescription
                  [:gco:CharacterString (str (or (:TypeOfContent detail) "Other") "|||" (:ContentDescription detail))]]
                 [:gmd:result {:gco:nilReason "missing"}]]]))
           details)

     ;; 3. Lineage Statement Element (Only if summary has non-blank string content)
     (when-not (string/blank? summary)
       [[:gmd:lineage
         [:gmd:LI_Lineage
          [:gmd:statement [:gco:CharacterString summary]]]]]))))