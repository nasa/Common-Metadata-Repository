(ns cmr.indexer.data.concept-parser
  "Contains helper functions to parse a concept for indexing."
  (:require [clojure.edn :as edn]
            [cmr.common.mime-types :as mt]
            [cmr.umm.core :as umm]
            [cmr.umm-spec.core :as umm-spec]))

(defmulti parse-concept
  "Parse the metadata from a concept map into a UMM model or map containing data needed for
  indexing."
  (fn [concept]
    (keyword (:concept-type concept))))

(defmethod parse-concept :tag
  [concept]
  (edn/read-string (:metadata concept)))

(defmethod parse-concept :default
  [concept]
  (if (= (:format concept) mt/umm-json)
    ;; For UMM-JSON metadata, we will use the umm-spec lib to convert it to ECHO10, then parse it
    ;; with the old umm lib for indexing. This will be updated when umm-spec is ready to handle all
    ;; metadata ingest and indexing.
    (let [model (umm-spec/parse-metadata (:concept-type concept) :umm-json (:metadata concept))
          echo10-metadata (umm-spec/generate-metadata (:concept-type concept) :echo10 model)]
      (umm/parse-concept (assoc concept
                                :format mt/echo10
                                :metadata echo10-metadata)))
    (umm/parse-concept concept)))
