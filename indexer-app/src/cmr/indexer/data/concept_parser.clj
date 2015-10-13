(ns cmr.indexer.data.concept-parser
  "Contains helper functions to parse a concept for indexing."
  (:require [clojure.edn :as edn]
            [cmr.umm.core :as umm]))

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
  (umm/parse-concept concept))