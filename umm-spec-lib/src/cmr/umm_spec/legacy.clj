(ns cmr.umm-spec.legacy
  "Functions for parsing concepts where old-style UMM is expected but new umm-spec formats (like
  JSON) need to be supported."
  (:require [cmr.common.mime-types :as mt]
            [cmr.umm.umm-core :as umm]
            [cmr.umm-spec.umm-spec-core :as umm-spec]))

(defn- parse-umm-json-concept
  [context {:keys [concept-type metadata format] :as concept-map}]
  (if (= :granule concept-type)
    (umm-spec/parse-metadata context concept-type format metadata)
    ;; Convert the UMM JSON metadata into ECHO10 metadata using umm-spec-lib, and then use the old
    ;; umm-lib to parse it into a UMM record.
    (let [model (umm-spec/parse-metadata context concept-type format metadata)
          echo10-metadata (umm-spec/generate-metadata context model :echo10)]
      (umm/parse-concept (assoc concept-map :format mt/echo10 :metadata echo10-metadata)))))

(defn parse-concept
  "Returns UMM record from a concept map, like cmr.umm.umm-core/parse-concept, but supports additional
  formats via umm-spec lib."
  [context concept-map]
  (if (mt/umm-json? (:format concept-map))
    (parse-umm-json-concept context concept-map)
    (umm/parse-concept concept-map)))

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
  (if (= :umm-json format-key)
    (umm-spec/generate-metadata context umm format-key)
    (umm/umm->xml umm format-key)))
