(ns cmr.umm.umm-core
  "Functions to transform concepts between formats."
  (:require
   [cmr.common.mime-types :as mt]
   [cmr.common.xml :as cx]
   [cmr.umm.echo10.echo10-core :as echo10]
   [cmr.umm.echo10.echo10-collection :as echo10-c]
   [cmr.umm.echo10.granule :as echo10-g]
   [cmr.umm.dif.dif-core :as dif]
   [cmr.umm.dif10.dif10-core :as dif10]
   [cmr.umm.dif.dif-collection :as dif-c]
   [cmr.umm.dif10.dif10-collection :as dif10-c]
   [cmr.umm.iso-mends.iso-mends-core :as iso-mends]
   [cmr.umm.iso-mends.iso-mends-collection :as iso-mends-c]
   [cmr.umm.iso-mends.granule :as iso-mends-g]
   [cmr.umm.iso-smap.iso-smap-core :as iso-smap]
   [cmr.umm.iso-smap.iso-smap-collection :as iso-smap-c]
   [cmr.umm.iso-smap.granule :as iso-smap-g]
   [cmr.umm.umm-collection]
   [cmr.umm.umm-granule])
  (:import cmr.umm.umm_collection.UmmCollection
           cmr.umm.umm_granule.UmmGranule))

(defmulti item->concept-type
  "Returns the concept type of the item"
  (fn [item]
    (type item)))

(defmethod item->concept-type UmmCollection
  [item]
  :collection)

(defmethod item->concept-type UmmGranule
  [item]
  :granule)

(defn validate-granule-concept-xml
  "Validates the granule concept metadata against its xml schema."
  [concept]
  (let [{:keys [concept-type format metadata]} concept]
    (case concept-type
      :granule (condp = format
                 mt/echo10 (echo10-g/validate-xml metadata)
                 mt/iso-smap (iso-smap-g/validate-xml metadata)))))

(defn parse-concept
  "Convert a metadata db concept map into a umm record by parsing its metadata."
  [concept]
  (let [{:keys [concept-type format metadata]} concept]
    (condp = (keyword concept-type)
      :collection (condp = format
                    mt/echo10 (echo10-c/parse-collection metadata)
                    mt/dif (dif-c/parse-collection metadata)
                    mt/dif10 (dif10-c/parse-collection metadata)
                    mt/iso19115 (iso-mends-c/parse-collection metadata)
                    mt/iso-smap (iso-smap-c/parse-collection metadata))
      :granule (condp = format
                 mt/echo10 (echo10-g/parse-granule metadata)
                 mt/iso-smap (iso-smap-g/parse-granule metadata)))))

(defn parse-concept-temporal
  "Convert a metadata db concept map into the umm temporal record by parsing its metadata."
  [concept]
  (let [{:keys [concept-type format metadata]} concept]
    (condp = (keyword concept-type)
      :granule (condp = format
                 mt/echo10 (echo10-g/parse-temporal metadata)
                 mt/iso-smap (iso-smap-g/parse-temporal metadata)))))

(defn parse-concept-access-value
  "Convert a metadata db concept map into the access value by parsing its metadata."
  [concept]
  (let [{:keys [concept-type format metadata]} concept]
    (condp = (keyword concept-type)
      :granule (condp = format
                 mt/echo10 (echo10-g/parse-access-value metadata)
                 mt/iso-smap (iso-smap-g/parse-access-value metadata)))))

(defn umm->xml
  "Convert a umm record into xml of a given format."
  [umm metadata-format]
  (cx/remove-xml-processing-instructions
   (condp = metadata-format
     :echo10 (echo10/umm->echo10-xml umm)
     :dif (dif/umm->dif-xml umm)
     :dif10 (dif10/umm->dif10-xml umm)
     :iso19115 (iso-mends/umm->iso-mends-xml umm)
     :iso-smap (iso-smap/umm->iso-smap-xml umm))))
