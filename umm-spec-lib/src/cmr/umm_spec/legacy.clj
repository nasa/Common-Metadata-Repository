(ns cmr.umm-spec.legacy
  "Functions for parsing concepts where old-style UMM is expected but new umm-spec formats (like
  JSON) need to be supported."
  (:require
   [cmr.common.mime-types :as mt]
   [cmr.umm.umm-core :as umm]
   [cmr.umm-spec.umm-spec-core :as umm-spec]))

(defn- parse-umm-json-concept
  [context {:keys [concept-type metadata format] :as concept-map}]
  (umm-spec/parse-metadata context concept-type format metadata))

(defn parse-concept
  "Returns UMM record from a concept map, like cmr.umm.umm-core/parse-concept, but supports additional
  formats via umm-spec lib."
  [context concept-map]
  (if (mt/umm-json? (:format concept-map))
    (parse-umm-json-concept context concept-map)
    (umm/parse-concept concept-map)))

(defn parse-concept-temporal
  "Returns the UMM record's temporal from a concept map. Like
  cmr.umm.umm-core/parse-concept-temporal, but supports additional
  formats via umm-spec lib."
  [concept-map]
  (if (mt/umm-json? (:format concept-map))
    (umm-spec/parse-concept-temporal concept-map)
    (umm/parse-concept-temporal concept-map)))

(defn parse-concept-access-value
  "Returns the UMM record's access value from a concept map. Like
  cmr.umm.umm-core/parse-concept-access-value, but supports additional
  formats via umm-spec lib."
  [concept-map]
  (if (mt/umm-json? (:format concept-map))
    (umm-spec/parse-concept-access-value concept-map)
    (umm/parse-concept-access-value concept-map)))

(defmulti item->concept-type (fn [item] (type item)))

(defmethod item->concept-type cmr.umm_spec.models.umm_collection_models.UMM-C
  [_]
  :collection)

(defmethod item->concept-type :default
  [item]
  (umm/item->concept-type item))

(defmulti generate-metadata
  "Returns metadata string from UMM record (old or new)."
  (fn [context umm format-key]
    (type umm)))

(defmethod generate-metadata cmr.umm_spec.models.umm_collection_models.UMM-C
  [context umm format-key]
  (umm-spec/generate-metadata context umm format-key))

(defmethod generate-metadata :default
  [context umm format-key]
  (if (= :umm-json (mt/format-key format-key))
    (umm-spec/generate-metadata context umm format-key)
    (umm/umm->xml umm format-key)))

(defn validate-metadata
  "Validates the given metadata and returns a list of errors found."
  [concept-type fmt metadata]
  (let [format-key (mt/format-key fmt)]
    (if (or (= :umm-json format-key)
            (= :collection concept-type))
      (umm-spec/validate-metadata concept-type fmt metadata)
      ;; calls umm-lib to validate granule xml formats
      (umm/validate-granule-concept-xml {:concept-type :granule
                                         :format (mt/format->mime-type format-key)
                                         :metadata metadata}))))
