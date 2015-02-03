(ns cmr.umm.validation.utils
  "This contains utility methods for helping perform validations."
  (:require [cmr.common.validations.core :as v]
            [cmr.common.services.errors :as e]
            [clojure.string :as str])
  (:import cmr.umm.collection.UmmCollection
           cmr.umm.granule.UmmGranule))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility functions

(def umm-type->concept-type
  {UmmCollection :collection
   UmmGranule :granule})

(defn perform-validation
  "Validates the umm record returning a list of error messages appropriate for the given metadata
  format and concept type. Returns an empty sequence if it is valid."
  [umm validation]
  (v/create-error-messages (v/validate validation umm)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Common validations

(defn unique-by-name-validator
  "Validates a list of items is unique by a specified field. Takes the name field and returns a
  new validator."
  [name-field]
  (fn [field-path values]
    (let [freqs (frequencies (map name-field values))]
      (when-let [duplicate-names (seq (for [[v freq] freqs :when (> freq 1)] v))]
        {field-path [(format "%%s must be unique. This contains duplicates named [%s]."
                             (str/join ", " duplicate-names))]}))))






