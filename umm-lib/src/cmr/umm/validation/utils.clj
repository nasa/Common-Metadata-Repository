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

(defn has-parent-validator
  "Validates that the given list of items has the parent attribute set.  Takes the name of the
  field to include in the error message.  For example :short-name or :name depending on the field
  being validated."
  [child-type parent-type field-for-err-msg]
  (fn [field-path values]
    (let [missing-parent-list
          (->> (map #(when-not (:parent %) (field-for-err-msg %)) values)
               (remove nil?)
               (seq))]
      (when missing-parent-list
        {field-path
         [(format "%%s referenced in a %s must be present in the parent %s. The invalid %ss are [%s]."
                  (name child-type) (name parent-type) (name field-for-err-msg)
                  (str/join ", " missing-parent-list))]}))))



