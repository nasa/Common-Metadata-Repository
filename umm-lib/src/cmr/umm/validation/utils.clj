(ns cmr.umm.validation.utils
  "This contains utility methods for helping perform validations."
  (:require [cmr.common.validations.core :as v]
            [cmr.common.services.errors :as e]
            [clojure.string :as str]
            [camel-snake-kebab :as csk])
  (:import cmr.umm.collection.UmmCollection
           cmr.umm.granule.UmmGranule))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility functions

(defn perform-validation
  "Validates the umm record returning a sequence of errors. Each error contains a path through the
  UMM model and a list of errors at that path. Returns an empty sequence if it is valid."
  [umm validation]
  (for [[field-path errors] (v/validate validation umm)]
    (e/map->PathErrors
      {:path field-path
       :errors (map (partial v/create-error-message field-path) errors)})))

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






