(ns cmr.indexer.data.concept-parser
  "Contains helper functions to parse a concept for indexing."
  (:require
   [clojure.edn :as edn]
   [cmr.umm-spec.umm-spec-core :as umm]
   [cmr.umm-spec.legacy :as umm-legacy]))

(defmulti parse-concept
  "Parse the metadata from a concept map into a UMM model or map containing data needed for
  indexing."
  (fn [context concept]
    (keyword (:concept-type concept))))

(defmethod parse-concept :tag
  [context concept]
  (edn/read-string (:metadata concept)))

(defmethod parse-concept :tag-association
  [context concept]
  (edn/read-string (:metadata concept)))

(defmethod parse-concept :collection
  [context concept]
  (umm/parse-metadata context concept))

(defmethod parse-concept :default
 [context concept]
 (umm-legacy/parse-concept context concept))
