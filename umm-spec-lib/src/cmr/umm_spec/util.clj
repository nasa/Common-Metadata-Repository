(ns cmr.umm-spec.util
  "This contains utilities for the UMM Spec code."
  (:require [cheshire.core :as json]
            [cheshire.factory :as factory]
            [cmr.common.util :as util]))

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