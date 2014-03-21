(ns cmr.metadata-db.data.utility
  "Utitly methods for concepts."
  (:require [clojure.string :as string]))

;;; Constants
(def concept-id-prefix-length 1)

;;; Utility methods
(defn concept-type-prefix
  "Truncate and upcase a concept-type to create a prefix for concept-ids"
  [concept-type-keyword]
  (let [concept-type (name concept-type-keyword)]
    (string/upper-case (subs concept-type 0 (min (count concept-type) concept-id-prefix-length)))))

(defn generate-concept-id
  "Generate a concept-id given concept type, sequence number, and provider id."
  [concept-type provider-id seq-num]
  (str (concept-type-prefix concept-type) seq-num "-" provider-id))