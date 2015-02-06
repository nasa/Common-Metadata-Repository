(ns cmr.umm.validation.utils
  "This contains utility methods for helping perform validations."
  (:require [clojure.string :as str]))


(defn unique-by-name-validator
  "Validates a list of items is unique by a specified field. Takes the name field and returns a
  new validator."
  [name-field]
  (fn [field-path values]
    (let [freqs (frequencies (map name-field values))]
      (when-let [duplicate-names (seq (for [[v freq] freqs :when (> freq 1)] v))]
        {field-path [(format "%%s must be unique. This contains duplicates named [%s]."
                             (str/join ", " duplicate-names))]}))))






