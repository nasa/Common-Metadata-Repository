(ns cmr.umm-spec.core
  "Contains functions for parsing, generating and validating metadata of various metadata formats."
  (:require [cmr.umm-spec.json-schema :as js]
            [clojure.java.io :as io]
            [cmr.common.xml :as cx]

    ;; XML -> UMM
            [cmr.umm-spec.simple-xpath :as xpath]
            [cmr.umm-spec.xml-to-umm-mappings.echo10 :as echo10-to-umm]
            [cmr.umm-spec.xml-to-umm-mappings.iso19115-2 :as iso19115-2-to-umm]
            [cmr.umm-spec.xml-to-umm-mappings.iso-smap :as iso-smap-to-umm]
            [cmr.umm-spec.xml-to-umm-mappings.dif9 :as dif9-to-umm]
            [cmr.umm-spec.xml-to-umm-mappings.dif10 :as dif10-to-umm]
            [cmr.umm-spec.xml-to-umm-mappings.serf :as serf-to-umm]

    ;; UMM -> XML
            [cmr.umm-spec.umm-to-xml-mappings.echo10 :as umm-to-echo10]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2 :as umm-to-iso19115-2]
            [cmr.umm-spec.umm-to-xml-mappings.iso-smap :as umm-to-iso-smap]
            [cmr.umm-spec.umm-to-xml-mappings.dif9 :as umm-to-dif9]
            [cmr.umm-spec.umm-to-xml-mappings.dif10 :as umm-to-dif10]
            [cmr.umm-spec.umm-to-xml-mappings.serf :as umm-to-serf]

    ;; UMM and JSON
            [cmr.umm-spec.umm-json :as umm-json]
            [cmr.umm-spec.versioning :as ver]
            [cmr.common.mime-types :as mt]))

(defn concept-type
  "Returns a concept type keyword from a UMM Clojure record (i.e. defrecord)."
  [record]
  (condp instance? record
    cmr.umm_spec.models.collection.UMM-C :collection
    cmr.umm_spec.models.service.UMM-S :service))

(defn format-key
  [media-type-or-format-key]
  (if (keyword? media-type-or-format-key)
    media-type-or-format-key
    (mt/format-key media-type-or-format-key)))

(defn umm-json-version
  [media-type]
  ;; the media type passed into these functions may be a keyword like :echo10 or a string with
  ;; parameters like umm+json;version=1.1, or just :umm-json
  (if (= :umm-json media-type)
    ver/current-version
    (or (mt/version media-type) ver/current-version)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validate Metadata

(def concept-type+metadata-format->schema
  {[:collection :echo10] (io/resource "xml-schemas/echo10/Collection.xsd")
   [:collection :dif] (io/resource "xml-schemas/dif9/dif_v9.9.3.xsd")
   [:collection :dif10] (io/resource "xml-schemas/dif10/dif_v10.2.xsd")
   [:collection :iso19115] (io/resource "xml-schemas/iso19115_2/schema/1.0/ISO19115-2_EOS.xsd")
   [:collection :iso-smap] (io/resource "xml-schemas/iso_smap/schema.xsd")
   [:service :serf] (io/resource "xml-schemas/serf/serf_v9.9.3.xsd")})

(defn validate-xml
  "Validates the XML against the xml schema for the given concept type and format."
  [concept-type metadata-format xml]
  (cx/validate-xml (concept-type+metadata-format->schema [concept-type metadata-format]) xml))

(defn validate-metadata
  "Validates the given metadata and returns a list of errors found."
  [concept-type media-type metadata]
  (let [format-key (format-key media-type)]
    (println "concept-type =" concept-type "media-type =" media-type)
    (if (= format-key :umm-json)
      (js/validate-umm-json metadata concept-type (umm-json-version media-type))
      (validate-xml concept-type format-key metadata))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parse Metadata

(defn- dispatch-parse-metadata
  [concept-type media-type metadata]
  ;; dispatch on a vector of concept type and format key
  [(keyword concept-type) (format-key media-type)])

(defmulti parse-metadata
  "Parses metadata of the specific concept type and format into UMM records"
  #'dispatch-parse-metadata)

(defmethod parse-metadata [:collection :umm-json]
  [_ media-type json]
  (umm-json/json->umm :collection json (umm-json-version media-type)))

(defmethod parse-metadata [:collection :echo10]
  [_ _ metadata]
  (echo10-to-umm/echo10-xml-to-umm-c (xpath/context metadata)))

(defmethod parse-metadata [:collection :dif]
  [_ _ metadata]
  (dif9-to-umm/dif9-xml-to-umm-c (xpath/context metadata)))

(defmethod parse-metadata [:collection :dif10]
  [_ _ metadata]
  (dif10-to-umm/dif10-xml-to-umm-c (xpath/context metadata)))

(defmethod parse-metadata [:collection :iso19115]
  [_ _ metadata]
  (iso19115-2-to-umm/iso19115-2-xml-to-umm-c (xpath/context metadata)))

(defmethod parse-metadata [:collection :iso-smap]
  [_ _ metadata]
  (iso-smap-to-umm/iso-smap-xml-to-umm-c (xpath/context metadata)))

(defmethod parse-metadata [:service :serf]
  [_ _ metadata]
  (serf-to-umm/serf-xml-to-umm-s (xpath/context metadata)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generate Metadata

(defmulti ^:private generate-metadata-impl
  (fn [concept-type metadata-standard umm]
    [(keyword concept-type) metadata-standard]))

(defmethod generate-metadata-impl [:collection :umm-json]
  [_ _ umm]
  (umm-json/umm->json umm))

(defmethod generate-metadata-impl [:collection :echo10]
  [_ _ umm]
  (umm-to-echo10/umm-c-to-echo10-xml umm))

(defmethod generate-metadata-impl [:collection :dif]
  [_ _ umm]
  (umm-to-dif9/umm-c-to-dif9-xml umm))

(defmethod generate-metadata-impl [:collection :dif10]
  [_ _ umm]
  (umm-to-dif10/umm-c-to-dif10-xml umm))

(defmethod generate-metadata-impl [:collection :iso19115]
  [_ _ umm]
  (umm-to-iso19115-2/umm-c-to-iso19115-2-xml umm))

(defmethod generate-metadata-impl [:collection :iso-smap]
  [_ _ umm]
  (umm-to-iso-smap/umm-c-to-iso-smap-xml umm))

(defmethod generate-metadata-impl [:service :serf]
  [_ _ umm]
  (umm-to-serf/umm-s-to-serf-xml umm))

(defn generate-metadata
  [umm media-type]
  (generate-metadata-impl (concept-type umm) (format-key media-type) umm))
