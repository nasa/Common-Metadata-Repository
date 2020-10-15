(ns cmr.umm-spec.umm-spec-core
  "Contains functions for parsing, generating and validating metadata of various metadata formats."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [cmr.common.mime-types :as mt]
   [cmr.common.xml :as cx]
   [cmr.common.xml.simple-xpath :as xpath]
   [cmr.umm-spec.dif-util :as dif-util]
   [cmr.umm-spec.json-schema :as js]
   [cmr.umm-spec.migration.version.core :as vm]
   [cmr.umm-spec.umm-g.granule :as umm-g]
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
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2 :as iso19115-2-to-umm]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.use-constraints :as use-constraints]
   ;; Added this to force the loading of the class, so that in CI build, it won't complain about
   ;; "No implementation of method: :validate of protocol: #'cmr.spatial.validation/SpatialValidation
   ;; found for class: cmr.spatial.cartesian_ring.CartesianRing."
   [cmr.spatial.ring-validations])
  (:import
   (cmr.umm.umm_granule UmmGranule)
   (cmr.umm_spec.models.umm_collection_models UMM-C)
   (cmr.umm_spec.models.umm_granule_models UMM-G)
   (cmr.umm_spec.models.umm_service_models UMM-S)
   (cmr.umm_spec.models.umm_subscription_models UMM-Sub)
   (cmr.umm_spec.models.umm_tool_models UMM-T)
   (cmr.umm_spec.models.umm_variable_models UMM-Var)))

(defn- concept-type
  "Returns a concept type keyword from a UMM Clojure record (i.e. defrecord)."
  [record]
  (condp instance? record
    UMM-C :collection
    ;; UMM-G record are used when UMM-G record is migrated to another UMM-G version
    UMM-G :granule
    UmmGranule :granule
    UMM-S :service
    UMM-Sub :subscription
    UMM-T :tool
    UMM-Var :variable))

(defn umm-json-version
  "Returns the UMM JSON version of the given media type. The media type may be a keyword like :echo10
  or a string like umm+json;version=1.1, or a map like {:format :umm-json :version \"1.2\"}"
  [concept-type media-type]
  (if (map? media-type)
    (if-let [version (:version media-type)]
      version
      (ver/current-version concept-type))
    (if (= :umm-json media-type)
      (ver/current-version concept-type)
      (or (mt/version-of media-type) (ver/current-version concept-type)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validate Metadata

(def concept-type+metadata-format->schema
  {[:collection :echo10] (io/resource "xml-schemas/echo10/Collection.xsd")
   [:collection :dif] (io/resource "xml-schemas/dif9/dif_v9.9.3.xsd")
   [:collection :dif10] (io/resource "xml-schemas/dif10/dif_v10.2.xsd")
   [:collection :iso19115] (io/resource "xml-schemas/iso19115_2/schema/1.0/ISO19115-2_EOS.xsd")
   [:collection :iso-smap] (io/resource "xml-schemas/iso_smap/schema.xsd")
   [:granule :echo10] (io/resource "xml-schemas/echo10/Granule.xsd")
   [:granule :iso-smap] (io/resource "xml-schemas/iso_smap/schema.xsd")})

(defn validate-xml
  "Validates the XML against the xml schema for the given concept type and format."
  [concept-type metadata-format xml]
  (cx/validate-xml (concept-type+metadata-format->schema [concept-type metadata-format]) xml))

(defn validate-metadata
  "Validates the given metadata and returns a list of errors found."
  [concept-type fmt metadata]
  (let [format-key (mt/format-key fmt)]
    (if (= :umm-json format-key)
      (js/validate-umm-json metadata concept-type (umm-json-version concept-type fmt))
      (validate-xml concept-type format-key metadata))))

(defn- parse-umm-g-metadata
  "Parses UMM-G metadata into umm-lib granule model"
  [context fmt metadata]
  (let [parsed-umm-g (umm-json/json->umm
                      context :granule metadata (umm-json-version :granule fmt))]
    ;; convert parsed umm-g into umm-lib granule model
    (umm-g/umm-g->Granule parsed-umm-g)))

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
     [:collection :umm-json] (umm-json/json->umm
                              context :collection metadata (umm-json-version :collection fmt))
     [:collection :echo10]   (echo10-to-umm/echo10-xml-to-umm-c
                              context (xpath/context metadata) options)
     [:collection :dif]      (dif9-to-umm/dif9-xml-to-umm-c (xpath/context metadata) options)
     [:collection :dif10]    (dif10-to-umm/dif10-xml-to-umm-c (xpath/context metadata) options)
     [:collection :iso19115] (iso19115-2-to-umm/iso19115-2-xml-to-umm-c
                              context (xpath/context metadata) options)
     [:collection :iso-smap] (iso-smap-to-umm/iso-smap-xml-to-umm-c (xpath/context metadata) options)
     [:granule :umm-json]    (parse-umm-g-metadata context fmt metadata)
     [:variable :umm-json]   (umm-json/json->umm
                              context :variable metadata (umm-json-version :variable fmt))
     [:service :umm-json]   (umm-json/json->umm
                             context :service metadata (umm-json-version :service fmt))
     [:tool :umm-json]   (umm-json/json->umm
                          context :tool metadata (umm-json-version :tool fmt))
     [:subscription :umm-json]   (umm-json/json->umm
                                  context :subscription metadata (umm-json-version :subscription fmt)))))

(defn- generate-umm-g-metadata
  "Generate UMM-G metadata from umm-lib granule model or UMM-G record."
  [context source-version fmt umm]
  (let [parsed-umm-g (if (instance? UMM-G umm)
                       umm
                       (umm-g/Granule->umm-g umm))
        target-umm-json-version (umm-json-version :granule fmt)]
    ;; migrate parsed umm-g to the version specified in format
    (umm-json/umm->json
     (vm/migrate-umm context :granule source-version target-umm-json-version parsed-umm-g))))

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
         source-version (or source-version (ver/current-version concept-type))]
     (condp = [concept-type (mt/format-key fmt)]
       [:collection :umm-json] (umm-json/umm->json (vm/migrate-umm context
                                                                   concept-type
                                                                   source-version
                                                                   (umm-json-version :collection fmt)
                                                                   umm))
       [:collection :echo10]   (umm-to-echo10/umm-c-to-echo10-xml umm)
       [:collection :dif]      (umm-to-dif9/umm-c-to-dif9-xml umm)
       [:collection :dif10]    (umm-to-dif10/umm-c-to-dif10-xml umm)
       [:collection :iso19115] (umm-to-iso19115-2/umm-c-to-iso19115-2-xml umm)
       [:collection :iso-smap] (umm-to-iso-smap/umm-c-to-iso-smap-xml umm)
       [:granule :umm-json]    (generate-umm-g-metadata context source-version fmt umm)
       [:variable :umm-json]   (umm-json/umm->json (vm/migrate-umm context
                                                                   concept-type
                                                                   source-version
                                                                   (umm-json-version :variable fmt)
                                                                   umm))
       [:service :umm-json]   (umm-json/umm->json (vm/migrate-umm context
                                                                  concept-type
                                                                  source-version
                                                                  (umm-json-version :service fmt)
                                                                  umm))
       [:tool :umm-json]   (umm-json/umm->json (vm/migrate-umm context
                                                               concept-type
                                                               source-version
                                                               (umm-json-version :tool fmt)
                                                               umm))
       [:subscription :umm-json]   (umm-json/umm->json (vm/migrate-umm context
                                                                       concept-type
                                                                       source-version
                                                                       (umm-json-version :subscription fmt)
                                                                       umm))))))

(defn parse-concept-temporal
  "Convert a metadata db concept map into the umm temporal record by parsing its metadata."
  [concept]
  (let [{:keys [concept-type format metadata]} concept
        mime-type (mt/base-mime-type-of format)]
    (condp = (keyword concept-type)
      :collection (condp = mime-type
                    mt/echo10 (echo10-to-umm/parse-temporal metadata)
                    mt/dif (dif9-to-umm/parse-temporal-extents metadata true)
                    mt/dif10 (dif10-to-umm/parse-temporal-extents metadata true)
                    mt/iso19115 (iso19115-2-to-umm/parse-doc-temporal-extents metadata)
                    mt/iso-smap (iso-smap-to-umm/parse-temporal-extents (first (xpath/select metadata iso-smap-to-umm/md-identification-base-xpath)))
                    mt/umm-json (:TemporalExtents (json/parse-string metadata true))
                    nil)
      :granule (condp = mime-type
                 mt/umm-json (umm-g/umm-g->Temporal (json/parse-string metadata true))))))

(defn parse-concept-access-value
  "Convert a metadata db concept map into the access value by parsing its metadata."
  [concept]
  (let [{:keys [concept-type format metadata]} concept
        mime-type (mt/base-mime-type-of format)]
    (condp = (keyword concept-type)
      :collection (condp = (mt/base-mime-type-of format)
                    mt/echo10 (echo10-to-umm/parse-access-constraints metadata true)
                    mt/dif (dif-util/parse-access-constraints metadata true)
                    mt/dif10 (dif-util/parse-access-constraints metadata true)
                    mt/iso19115 (use-constraints/parse-access-constraints
                                 metadata
                                 iso19115-2-to-umm/constraints-xpath
                                 true)
                    mt/iso-smap (use-constraints/parse-access-constraints
                                 metadata
                                 iso-smap-to-umm/constraints-xpath
                                 true)
                    mt/umm-json (:AccessConstraints (json/parse-string metadata true))
                    nil)
      :granule (condp = mime-type
                 mt/umm-json (get-in (json/parse-string metadata true) [:AccessConstraints :Value])))))
