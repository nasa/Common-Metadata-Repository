(ns cmr.umm-spec.legacy
  "Functions for parsing concepts where old-style UMM is expected but new umm-spec formats (like
  JSON) need to be supported."
  (:require [cmr.common.mime-types :as mt]
            [cmr.umm.core :as umm]
            [cmr.umm-spec.core :as umm-spec]))

(defn- parse-umm-json-concept
  [{:keys [concept-type metadata] :as concept-map}]
  ;; Convert the UMM JSON metadata into ECHO10 metadata using umm-spec-lib, and then use the old
  ;; umm-lib to parse it into a UMM record.
  (let [model (umm-spec/parse-metadata concept-type :umm-json metadata)
        echo10-metadata (umm-spec/generate-metadata model :echo10)]
    (umm/parse-concept (assoc concept-map :format mt/echo10 :metadata echo10-metadata))))

(defn parse-concept
  "Returns UMM record from a concept map, like cmr.umm.core/parse-concept, but supports additional
  formats via umm-spec lib."
  [concept-map]
  (if (= mt/umm-json (:format concept-map))
    (parse-umm-json-concept concept-map)
    (umm/parse-concept concept-map)))

(defmulti item->concept-type (fn [item] (type item)))

(defmethod item->concept-type cmr.umm_spec.models.collection.UMM-C
  [_]
  :collection)

(defmethod item->concept-type :default
  [item]
  (umm/item->concept-type item))

(defmulti generate-metadata
  "Returns metadata string from UMM record (old or new)."
  (fn [umm format-key] (type umm)))

(defmethod generate-metadata cmr.umm_spec.models.collection.UMM-C
  [umm format-key]
  (umm-spec/generate-metadata umm format-key))

(defmethod generate-metadata :default
  [umm format-key]
  (umm/umm->xml umm format-key))
