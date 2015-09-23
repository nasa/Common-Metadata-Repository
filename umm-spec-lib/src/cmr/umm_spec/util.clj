(ns cmr.umm-spec.util
  "This contains utilities for the UMM Spec code."
  (:require [cheshire.core :as json]
            [cheshire.factory :as factory]
            [cmr.common.util :as util]
            [cmr.umm-spec.xml.parse :as p]))

(def not-provided
  "place holder string value for not provided string field"
  "Not provided")

(def not-provided
  "place holder string value for not provided string field"
  "Not provided")

(defn load-json-resource
  "Loads a json resource from the classpath. The JSON file may contain comments which are ignored"
  [json-resource]
  (binding [factory/*json-factory* (factory/make-json-factory
                                     {:allow-comments true})]
    (json/decode (slurp json-resource) true)))

(defn convert-empty-record-to-nil
  "Converts empty record to nil."
  [record]
  (if (seq (util/remove-nil-keys record))
    record
    nil))

(defn remove-empty-records
  "Returns the given records with empty records removed from it."
  [records]
  (->> records
       (keep convert-empty-record-to-nil)
       seq))

(defn with-default
  "Returns the value if it exists or returns the default value 'Not provided'."
  [value]
  (if (some? value)
    value
    not-provided))

(defn without-default-value-of
  "Returns the parsed value of the given doc on the given xpath and converting the 'Not provided'
  default value to nil."
  [doc xpath]
  (let [value (p/value-of doc xpath)]
    (when-not (= value not-provided)
      value)))

(defn nil-to-empty-string
  "Returns the string itself or empty string if it is nil."
  [s]
  (if (some? s) s ""))
