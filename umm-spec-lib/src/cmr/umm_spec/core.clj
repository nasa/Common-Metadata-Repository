(ns cmr.umm-spec.core
  (:require [cmr.umm-spec.json-schema :as js]

            ;; XML -> UMM
            [cmr.umm-spec.xml-to-umm-mappings.parser :as xp]
            [cmr.umm-spec.xml-to-umm-mappings.echo10 :as echo10-to-umm]
            [cmr.umm-spec.xml-to-umm-mappings.iso19115-2 :as iso19115-2-to-umm]
            [cmr.umm-spec.xml-to-umm-mappings.iso-smap :as iso-smap-to-umm]
            [cmr.umm-spec.xml-to-umm-mappings.dif9 :as dif9-to-umm]
            [cmr.umm-spec.xml-to-umm-mappings.dif10 :as dif10-to-umm]

            ;; UMM -> XML
            [cmr.umm-spec.umm-to-xml-mappings.xml-generator :as xg]
            [cmr.umm-spec.umm-to-xml-mappings.echo10 :as umm-to-echo10]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2 :as umm-to-iso19115-2]
            [cmr.umm-spec.umm-to-xml-mappings.iso-smap :as umm-to-iso-smap]
            [cmr.umm-spec.umm-to-xml-mappings.dif9 :as umm-to-dif9]
            [cmr.umm-spec.umm-to-xml-mappings.dif10 :as umm-to-dif10]

            ;; UMM and JSON
            [cmr.umm-spec.umm-json :as umm-json]

            ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parse Metadata

(defmulti parse-metadata
  "Parses metadata of the specific concept type and format into UMM records"
  (fn [concept-type metadata-standard metadata]
    [(keyword concept-type) metadata-standard]))

(defmethod parse-metadata [:collection :umm-json]
  [_ _ json]
  (umm-json/json->umm js/umm-c-schema json))

(defmethod parse-metadata [:collection :echo10]
  [_ _ metadata]
  (xp/parse-xml echo10-to-umm/echo10-xml-to-umm-c metadata))

(defmethod parse-metadata [:collection :dif]
  [_ _ metadata]
  (xp/parse-xml dif9-to-umm/dif9-xml-to-umm-c metadata))

(defmethod parse-metadata [:collection :dif10]
  [_ _ metadata]
  (xp/parse-xml dif10-to-umm/dif10-xml-to-umm-c metadata))

(defmethod parse-metadata [:collection :iso19115]
  [_ _ metadata]
  (xp/parse-xml iso19115-2-to-umm/iso19115-2-xml-to-umm-c metadata))

(defmethod parse-metadata [:collection :iso-smap]
  [_ _ metadata]
  (xp/parse-xml iso-smap-to-umm/iso-smap-xml-to-umm-c metadata))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generate Metadata

(defmulti generate-metadata
  (fn [concept-type metadata-standard umm]
    [(keyword concept-type) metadata-standard]))

(defmethod generate-metadata [:collection :umm-json]
  [_ _ umm]
  (umm-json/umm->json umm))

(defmethod generate-metadata [:collection :echo10]
  [_ _ umm]
  (xg/generate-xml umm-to-echo10/umm-c-to-echo10-xml umm))

(defmethod generate-metadata [:collection :dif]
  [_ _ umm]
  (xg/generate-xml umm-to-dif9/umm-c-to-dif9-xml umm))

(defmethod generate-metadata [:collection :dif10]
  [_ _ umm]
  (xg/generate-xml umm-to-dif10/umm-c-to-dif10-xml umm))

(defmethod generate-metadata [:collection :iso19115]
  [_ _ umm]
  (xg/generate-xml umm-to-iso19115-2/umm-c-to-iso19115-2-xml umm))

(defmethod generate-metadata [:collection :iso-smap]
  [_ _ umm]
  (xg/generate-xml umm-to-iso-smap/umm-c-to-iso-smap-xml umm))
