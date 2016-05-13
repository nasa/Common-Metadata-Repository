(ns cmr.umm-spec.xml-to-umm-mappings.dif10.paleo-temporal
  "Defines mappings from DIF 10 Paleo_Temporal_Coverage elements into UMM records"
  (:require [cmr.umm-spec.xml-to-umm-mappings.dif9.paleo-temporal :as dif9]))

(defn parse-paleo-temporal
  "Returns UMM-C PaleoTemporalCoverage map from DIF 10 XML document."
  [doc]
  (dif9/parse-paleo-temporal-from-xpath doc "/DIF/Temporal_Coverage/Paleo_DateTime"))