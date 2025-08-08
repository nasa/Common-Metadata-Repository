(ns cmr.indexer.data.concept-parser
  "Contains helper functions to parse a concept for indexing."
  (:require
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [clojure.string :as string]
   [cmr.common.concepts :as concepts]
   [cmr.umm-spec.umm-spec-core :as umm]
   [cmr.umm-spec.legacy :as umm-legacy]))

(defmulti parse-concept
  "Parse the metadata from a concept map into a UMM model or map containing data needed for
  indexing."
  (fn [_context concept]
    (keyword (:concept-type concept))))

(defmethod parse-concept :tag
  [_context concept]
  (edn/read-string (:metadata concept)))

(defmethod parse-concept :tag-association
  [_context concept]
  (edn/read-string (:metadata concept)))

(defmethod parse-concept :variable
  [context concept]
  (umm/parse-metadata context concept))

(defmethod parse-concept :variable-association
  [_context concept]
  (edn/read-string (:metadata concept)))

(defmethod parse-concept :service
  [context concept]
  (umm/parse-metadata context concept))

(defmethod parse-concept :service-association
  [_context concept]
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
  [_context concept]
  (edn/read-string (:metadata concept)))

(defmethod parse-concept :generic-association
  [_context concept]
  (edn/read-string (:metadata concept)))

(doseq [concept-type concepts/get-generic-non-draft-concept-types-array]
  (defmethod parse-concept concept-type
    [_context concept]
    (json/parse-string (:metadata concept) true)))

(doseq [concept-type concepts/get-draft-concept-types-array]
  (defmethod parse-concept concept-type
    [context concept]
    ;; If the draft record is a json record, then parse it,
    ;; otherwise figure out what the draft record concept type is
    ;; and let the parsing code parse it to umm-c.
    (if (string/includes? (:format concept) "json")
      (json/parse-string (:metadata concept) true)
      (let [concept-type (:concept-type concept)]
        (if (concepts/is-draft-concept? concept-type)
          (let [draft-concept-type (concepts/get-concept-type-of-draft concept-type)
                concept (assoc concept :concept-type draft-concept-type)]
            (umm/parse-metadata context concept))
          (umm/parse-metadata context concept))))))

(defmethod parse-concept :default
  [context concept]
  (umm-legacy/parse-concept context concept))
