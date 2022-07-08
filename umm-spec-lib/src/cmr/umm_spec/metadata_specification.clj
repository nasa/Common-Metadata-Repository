(ns cmr.umm-spec.metadata-specification
  "Functions related to the MetadataSpecification node common to many of the umm
   models. Eventually all models will include this node, so a common set of
   functions is needed."
  (:require
   [cmr.umm-spec.versioning :as ver]))

(def types
  "This is a mapping between collection symbol types to names found in metadata"
  {:collection "UMM-C"  ; Reserved: currently does not have this node
   :granule "UMM-G"
   :service "UMM-S"
   :tool "UMM-T"

   :subscription "UMM-Sub"

   ;; UMM-V can not be used due to visualizations. Concept IDs start with V.
   :variable "UMM-Var"

   ;; Currently not implemented but this name is reserved for future use.
   ;; Concept Ids are not defined.
   :visualization "UMM-Vis"})

(defn metadata-spec-content
  "Create the fields which are inside a MetadataSpecification node. If version
  is not given, then it is assumed to be the latest as defined by
  cmr.umm-spec.versioning"
  ([umm-type] (metadata-spec-content umm-type (-> ver/versions umm-type last)))
  ([umm-type version]
   (let [code (get types umm-type)]
     {:URL (str "https://cdn.earthdata.nasa.gov/umm/" (name umm-type) "/v" version)
      :Name code
      :Version version})))

(defn update-version
  "At the very least, all metadata records need to update the metadata
   specification."
  [umm umm-type version]
  (assoc umm :MetadataSpecification (metadata-spec-content umm-type version)))
