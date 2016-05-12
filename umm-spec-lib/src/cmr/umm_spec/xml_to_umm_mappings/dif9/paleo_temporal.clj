(ns cmr.umm-spec.xml-to-umm-mappings.dif9.paleo-temporal
  "Defines mappings from DIF 9 Paleo_Temporal_Coverage elements into UMM records"
  (:require [cmr.common.xml.parse :refer :all]
            [cmr.common.xml.simple-xpath :refer [select text]]))

(defn parse-paleo-temporal-from-xpath
  "Returns UMM-C PaleoTemporalCoverage map from XML document for the given xpath string."
  [doc xpath]
  (when-let [paleos (select doc xpath)]
    (for [paleo paleos]
      {:StartDate (value-of paleo "Paleo_Start_Date")
       :EndDate (value-of paleo "Paleo_Stop_Date")
       :ChronostratigraphicUnits
       (when-let [units (select paleo "Chronostratigraphic_Unit")]
         (for [unit units]
           {:Eon (value-of unit "Eon")
            :Era (value-of unit "Era")
            :Period (value-of unit "Period")
            :Epoch (value-of unit "Epoch")
            :Stage (value-of unit "Stage")
            :DetailedClassification (value-of unit "Detailed_Classification")}))})))

(defn parse-paleo-temporal
  "Returns UMM-C PaleoTemporalCoverage map from DIF 9 XML document."
  [doc]
  (parse-paleo-temporal-from-xpath doc "/DIF/Paleo_Temporal_Coverage"))
