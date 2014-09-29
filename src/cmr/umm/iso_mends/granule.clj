(ns cmr.umm.iso-mends.granule
  "Contains functions for parsing and generating the MENDS ISO dialect."
  (:require [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [cmr.common.xml :as cx]
            [cmr.umm.iso-mends.core :as core]
            [cmr.umm.granule :as c]
            [cmr.common.xml :as v])
  (:import cmr.umm.granule.UmmGranule))


(extend-protocol cmr.umm.iso-mends.core/UmmToIsoMendsXml
  UmmGranule
  (umm->iso-mends-xml
    ([granule]
     (cmr.umm.iso-mends.core/umm->iso-mends-xml granule false))
    ([granule indent?]
     (let [{:keys [granule-ur]} granule
           emit-fn (if indent? x/indent-str x/emit-str)]
       (emit-fn
         (x/element :granule {}
                    (x/element :placeholder {} "UMM granule for MENDS ISO is not supported.")
                    (x/element :granule-ur {} granule-ur)))))))

(defn validate-xml
  "Validates the XML against the ISO schema."
  [xml]
  (v/validate-xml (io/resource "schema/iso_mends/schema/1.0/ISO19115-2_EOS.xsd") xml))

