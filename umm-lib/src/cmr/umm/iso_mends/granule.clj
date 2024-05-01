(ns cmr.umm.iso-mends.granule
  "Contains functions for parsing and generating the MENDS ISO dialect."
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [cmr.common.xml :as cx]
   [cmr.umm.iso-mends.iso-mends-core :as core]
   [cmr.umm.umm-granule :as granule])
  (:import cmr.umm.umm_granule.UmmGranule))

(extend-protocol core/UmmToIsoMendsXml
  UmmGranule
  (umm->iso-mends-xml
    ([granule]
     (let [{:keys [granule-ur]} granule]
       (xml/emit-str
         (xml/element :granule {}
                      (xml/element :placeholder {} "UMM granule for MENDS ISO is not supported.")
                      (xml/element :granule-ur {} granule-ur)))))))

(def schema-location "schema/iso_mends/schema/1.0/ISO19115-2_EOS.xsd")

(defn validate-xml
  "Validates the XML against the ISO schema."
  [xml]
  (cx/validate-xml (io/resource schema-location) xml))

(comment
  (granule/map->Track nil))
