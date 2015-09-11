(ns cmr.umm-spec.core
  "Contains functions for parsing, generating and validating metadata of various metadata formats."
  (:require [cmr.umm-spec.json-schema :as js]
            [clojure.java.io :as io]
            [cmr.common.xml :as cx]

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
;; Validate Metadata

(def concept-type+metadata-format->schema
  {[:collection :echo10] (io/resource "xml-schemas/echo10/Collection.xsd")
   [:collection :dif] (io/resource "xml-schemas/dif9/dif_v9.9.3.xsd")
   [:collection :dif10] (io/resource "xml-schemas/dif10/dif_v10.1.xsd")
   [:collection :iso19115] (io/resource "xml-schemas/iso19115_2/schema/1.0/ISO19115-2_EOS.xsd")
   [:collection :iso-smap] (io/resource "xml-schemas/iso_smap/schema.xsd")})

(defn validate-xml
  "Validates the XML against the xml schema for the given concept type and format."
  [concept-type metadata-format xml]
  (cx/validate-xml (concept-type+metadata-format->schema [concept-type metadata-format]) xml))

(defn validate-metadata
  "Validates the given metadata and returns a list of errors found."
  [concept-type metadata-standard metadata]
  (if (= metadata-standard :umm-json)
    (js/validate-umm-json metadata)
    (validate-xml concept-type metadata-standard metadata)))

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
  (echo10-to-umm/parse-echo10-xml metadata))

(defmethod parse-metadata [:collection :dif]
  [_ _ metadata]
  (xp/parse-xml dif9-to-umm/dif9-xml-to-umm-c metadata))

(defmethod parse-metadata [:collection :dif10]
  [_ _ metadata]
  (dif10-to-umm/dif10-xml-to-umm-c metadata))

(defmethod parse-metadata [:collection :iso19115]
  [_ _ metadata]
  (xp/parse-xml iso19115-2-to-umm/iso19115-2-xml-to-umm-c metadata))

(defmethod parse-metadata [:collection :iso-smap]
  [_ _ metadata]
  (iso-smap-to-umm/iso-smap-xml-to-umm-c metadata))

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
  (umm-to-echo10/echo10-xml umm))

(defmethod generate-metadata [:collection :dif]
  [_ _ umm]
  (xg/generate-xml umm-to-dif9/umm-c-to-dif9-xml umm))

(defmethod generate-metadata [:collection :dif10]
  [_ _ umm]
  (umm-to-dif10/umm-c-to-dif10-xml umm))

(defmethod generate-metadata [:collection :iso19115]
  [_ _ umm]
  (xg/generate-xml umm-to-iso19115-2/umm-c-to-iso19115-2-xml umm))

(defmethod generate-metadata [:collection :iso-smap]
  [_ _ umm]
  (xg/generate-xml umm-to-iso-smap/umm-c-to-iso-smap-xml umm))

