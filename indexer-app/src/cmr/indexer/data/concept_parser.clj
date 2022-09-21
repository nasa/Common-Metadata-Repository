(ns cmr.indexer.data.concept-parser
  "Contains helper functions to parse a concept for indexing."
  (:require 
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [cmr.common.concepts :as concepts]
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

(defmethod parse-concept :variable
  [context concept]
  (umm/parse-metadata context concept))

(defmethod parse-concept :variable-association
  [context concept]
  (edn/read-string (:metadata concept)))

(defmethod parse-concept :service
  [context concept]
  (umm/parse-metadata context concept))

(defmethod parse-concept :service-association
  [context concept]
  (edn/read-string (:metadata concept)))

(defmethod parse-concept :collection
  [context concept]
  (umm/parse-metadata context concept))

(defmethod parse-concept :subscription
  [context concept]
  (umm/parse-metadata context concept))

(defmethod parse-concept :tool
  [context concept]
  (umm/parse-metadata context concept))
 
(defmethod parse-concept :tool-association
  [context concept]
  (edn/read-string (:metadata concept)))

(defmethod parse-concept :generic-association
  [context concept]
  (edn/read-string (:metadata concept)))

(doseq [concept-type (concepts/get-generic-concept-types-array)]
  (defmethod parse-concept concept-type
    [context concept]
    (json/parse-string (:metadata concept) true)))

(defmethod parse-concept :default
 [context concept]
 (umm-legacy/parse-concept context concept))
