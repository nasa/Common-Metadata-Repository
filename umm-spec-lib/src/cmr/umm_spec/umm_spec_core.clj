(ns cmr.umm-spec.umm-spec-core
  "Contains functions for parsing, generating and validating metadata of various metadata formats."
  (:require
   [clojure.java.io :as io]
   [cmr.common.mime-types :as mt]
   [cmr.common.xml :as cx]
   [cmr.common.xml.simple-xpath :as xpath]
   [cmr.umm-spec.dif-util :as dif-util]
   [cmr.umm-spec.json-schema :as js]
   [cmr.umm-spec.migration.version-migration :as vm]
   [cmr.umm-spec.umm-json :as umm-json]
   [cmr.umm-spec.umm-to-xml-mappings.dif10 :as umm-to-dif10]
   [cmr.umm-spec.umm-to-xml-mappings.dif9 :as umm-to-dif9]
   [cmr.umm-spec.umm-to-xml-mappings.echo10 :as umm-to-echo10]
   [cmr.umm-spec.umm-to-xml-mappings.iso-smap :as umm-to-iso-smap]
   [cmr.umm-spec.umm-to-xml-mappings.iso19115-2 :as umm-to-iso19115-2]
   [cmr.umm-spec.util :as u]
   [cmr.umm-spec.versioning :as ver]
   [cmr.umm-spec.xml-to-umm-mappings.dif10 :as dif10-to-umm]
   [cmr.umm-spec.xml-to-umm-mappings.dif9 :as dif9-to-umm]
   [cmr.umm-spec.xml-to-umm-mappings.echo10 :as echo10-to-umm]
   [cmr.umm-spec.xml-to-umm-mappings.iso-smap :as iso-smap-to-umm]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2 :as iso19115-2-to-umm])
  (:import
   (cmr.umm_spec.models.umm_collection_models UMM-C)
   (cmr.umm_spec.models.umm_service_models UMM-S)
   (cmr.umm_spec.models.umm_variable_models UMM-Var)))

(defn concept-type
  "Returns a concept type keyword from a UMM Clojure record (i.e. defrecord)."
  [record]
  (condp instance? record
    UMM-C :collection
    UMM-S :service
    UMM-Var :variable))

(defn umm-json-version
  "Returns the UMM JSON version of the given media type. The media type may be a keyword like :echo10
  or a string like umm+json;version=1.1, or a map like {:format :umm-json :version \"1.2\"}"
  [media-type]
  (if (map? media-type)
    (if-let [version (:version media-type)]
      version
      ver/current-version)
    (if (= :umm-json media-type)
      ver/current-version
      (or (mt/version-of media-type) ver/current-version))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validate Metadata

(def concept-type+metadata-format->schema
  {[:collection :echo10] (io/resource "xml-schemas/echo10/Collection.xsd")
   [:collection :dif] (io/resource "xml-schemas/dif9/dif_v9.9.3.xsd")
   [:collection :dif10] (io/resource "xml-schemas/dif10/dif_v10.2.xsd")
   [:collection :iso19115] (io/resource "xml-schemas/iso19115_2/schema/1.0/ISO19115-2_EOS.xsd")
   [:collection :iso-smap] (io/resource "xml-schemas/iso_smap/schema.xsd")})

(defn validate-xml
  "Validates the XML against the xml schema for the given concept type and format."
  [concept-type metadata-format xml]
  (cx/validate-xml (concept-type+metadata-format->schema [concept-type metadata-format]) xml))

(defn validate-metadata
  "Validates the given metadata and returns a list of errors found."
  [concept-type fmt metadata]
  (let [format-key (mt/format-key fmt)]
    (if (= (mt/format-key fmt) :umm-json)
      (js/validate-umm-json metadata concept-type (umm-json-version fmt))
      (validate-xml concept-type format-key metadata))))

(defn parse-metadata
  "Parses metadata of the specific concept type and format into UMM records.
  The :sanitize? option tells the parsing code to apply the default values for fields
  when parsing the metadata into umm. It defaults to true."
  ([context concept]
   (let [{:keys [concept-type format metadata]} concept]
     (parse-metadata context concept-type format metadata)))
  ([context concept-type fmt metadata]
   (parse-metadata context concept-type fmt metadata u/default-parsing-options))
  ([context concept-type fmt metadata options]
   (condp = [concept-type (mt/format-key fmt)]
     [:collection :umm-json] (umm-json/json->umm context :collection metadata (umm-json-version fmt))
     [:collection :echo10]   (echo10-to-umm/echo10-xml-to-umm-c
                               context (xpath/context metadata) options)
     [:collection :dif]      (dif9-to-umm/dif9-xml-to-umm-c (xpath/context metadata) options)
     [:collection :dif10]    (dif10-to-umm/dif10-xml-to-umm-c (xpath/context metadata) options)
     [:collection :iso19115] (iso19115-2-to-umm/iso19115-2-xml-to-umm-c
                               context (xpath/context metadata) options)
     [:collection :iso-smap] (iso-smap-to-umm/iso-smap-xml-to-umm-c (xpath/context metadata) options)
     [:variable :umm-json]   (umm-json/json->umm context :variable metadata (umm-json-version fmt)))))

(defn generate-metadata
  "Returns the generated metadata for the given metadata format and umm record.
  * umm is the umm record that is parsed from the given source umm json schema version
  * fmt is the target format of the generated metadata, it would either be in mime type format
  (application/umm+json;version=1.1), a keyword (:echo10), or a map ({:format :umm-json, :version=\"1.1\"})
  * source-version if provided is the umm json schema version that the given umm record is in,
  defaults to the latest umm json schema version."
  ([context umm fmt]
   (generate-metadata context umm fmt nil))
  ([context umm fmt source-version]
   (let [concept-type (concept-type umm)
         source-version (or source-version ver/current-version)]
     (condp = [concept-type (mt/format-key fmt)]
       [:collection :umm-json] (umm-json/umm->json (vm/migrate-umm context
                                                                   concept-type
                                                                   source-version
                                                                   (umm-json-version fmt)
                                                                   umm))
       [:collection :echo10]   (umm-to-echo10/umm-c-to-echo10-xml umm)
       [:collection :dif]      (umm-to-dif9/umm-c-to-dif9-xml umm)
       [:collection :dif10]    (umm-to-dif10/umm-c-to-dif10-xml umm)
       [:collection :iso19115] (umm-to-iso19115-2/umm-c-to-iso19115-2-xml umm)
       [:collection :iso-smap] (umm-to-iso-smap/umm-c-to-iso-smap-xml umm)
       [:variable :umm-json]   (umm-json/umm->json umm)))))

(defn parse-collection-temporal
  "Convert a metadata db concept map into the umm temporal record by parsing its metadata."
  [concept]
  (let [{:keys [format metadata]} concept]
    (condp = format
     mt/echo10 (echo10-to-umm/parse-temporal metadata)
     mt/dif (dif9-to-umm/parse-temporal-extents metadata true)
     mt/dif10 (dif10-to-umm/parse-temporal-extents metadata true)
     mt/iso19115 (iso19115-2-to-umm/parse-doc-temporal-extents metadata)
     mt/iso-smap (iso-smap-to-umm/parse-temporal-extents metadata))))

(defn parse-collection-access-value
  "Convert a metadata db concept map into the access value by parsing its metadata."
  [concept]
  (let [{:keys [format metadata]} concept]
    (condp = format
     mt/echo10 (echo10-to-umm/parse-access-constraints metadata true)
     mt/dif (dif-util/parse-access-constraints metadata true)
     mt/dif10 (dif-util/parse-access-constraints metadata true)
     mt/iso19115 (iso19115-2-to-umm/parse-access-constraints metadata true)
     mt/iso-smap nil)))
