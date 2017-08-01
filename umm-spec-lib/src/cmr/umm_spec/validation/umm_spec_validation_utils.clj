(ns cmr.umm-spec.validation.umm-spec-validation-utils
  "This contains utility methods for helping perform validations."
  (:require
   [clojure.string :as str]
   [cmr.umm-spec.date-util :as date]))

(defn unique-by-name-validator
  "Validates a list of items is unique by a specified field. Takes the name field and returns a
  new validator."
  [name-field]
  (fn [field-path values]
    (let [freqs (frequencies (map name-field values))]
      (when-let [duplicate-names (seq (for [[v freq] freqs :when (> freq 1)] v))]
        {field-path [(format "%%s must be unique. This contains duplicates named [%s]."
                             (str/join ", " duplicate-names))]}))))

(defn date-in-past-validator
  "Validate that the date is in the past"
  [field-path value]
  (when (and value (not (date/is-in-past? value)))
    {field-path ["Date should be in the past."]}))

(defn date-in-future-validator
  "Validate that the date is in the future"
  [field-path value]
  (when (and value (not (date/is-in-future? value)))
    {field-path ["Date should be in the future."]}))

(defn escape-error-string
  "Escape any % in the string (for example those that occur in a URL) so that when the string is
  formatted, an exception is not thrown by the formatter"
  [s]
  (str/replace s "%" "%%"))
