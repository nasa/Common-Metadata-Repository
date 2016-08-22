(ns cmr.umm.validation.validation-utils
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

(defn has-parent-validator
  "Validates that the given list of items has the parent attribute set.  Takes the name of the
  field to include in the error message.  For example :short-name or :name depending on the field
  being validated.

  Example: (has-parent-validator :short-name \"Platform short name\")
  \"The following list of Platform short names did not exist in the referenced parent collection: [foo].\""
  [parent-ref-field human-readable-field-name]
  (fn [field-path values]
    (let [values (if (or (sequential? values) (nil? values)) values [values])
          missing-parent-list (->> values
                                   (remove :parent)
                                   (map parent-ref-field))]
      (when (seq missing-parent-list)
        {field-path
         [(format "The following list of %ss did not exist in the referenced parent collection: [%s]."
                  human-readable-field-name
                  (str/join ", " missing-parent-list))]}))))
