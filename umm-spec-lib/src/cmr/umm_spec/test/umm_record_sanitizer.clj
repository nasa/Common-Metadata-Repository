(ns cmr.umm-spec.test.umm-record-sanitizer
  "This contains functions for manipulating the generator generated umm-record to a sanitized
  version to pass the xml validation for various supported metadata format. This is needed because
  the incompatibility between UMM JSON schema and schemas of the various metadata formats making the
  generated metadata xml invalid without some kind of sanitization."
  (:require [cmr.common.util :as util :refer [update-in-each]]))

(defn- set-if-exist
  "Sets the field of the given record to the value if the field has a value, returns the record."
  [record field value]
  (if (field record)
    (assoc record field value)
    record))

(defn- sanitize-science-keywords
  "Temporary! We should be able to define the JSON schema in a way that ensures science keyword
  hierarchy is obeyed. It could potentially be done using a complex oneOf or anyOf."
  [record]
  (assoc record
         :ScienceKeywords (seq (for [sk (:ScienceKeywords record)]
                                 (cond
                                   (nil? (:VariableLevel1 sk))
                                   (assoc sk
                                          :VariableLevel2 nil
                                          :VariableLevel3 nil
                                          :DetailedVariable nil)

                                   (nil? (:VariableLevel2 sk))
                                   (assoc sk :VariableLevel3 nil)

                                   :else
                                   sk)))))

(defn sanitized-umm-record
  "Returns the sanitized version of the given umm record."
  [record]
  (-> record
      ;; DataLanguage should be from a list of enumerations which are not defined in UMM JSON schema
      ;; so here we just replace the generated value to eng to make it through the validation.
      (set-if-exist :DataLanguage "eng")
      (set-if-exist :CollectionProgress "COMPLETE")

      ;; Figure out if we can define this in the schema
      sanitize-science-keywords))
