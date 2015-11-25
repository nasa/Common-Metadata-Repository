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
        echo10-metadata (umm-spec/generate-metadata concept-type :echo10 model)]
    (umm/parse-concept (assoc concept-map :format mt/echo10 :metadata echo10-metadata))))

(defn parse-concept
  "Returns UMM record from a concept map, like cmr.umm.core/parse-concept, but supports additional
  formats via umm-spec lib."
  [concept-map]
  (if (= mt/umm-json (:format concept-map))
    (parse-umm-json-concept concept-map)
    (umm/parse-concept concept-map)))
