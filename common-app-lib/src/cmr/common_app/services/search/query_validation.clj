(ns cmr.common-app.services.search.query-validation
  "Defines protocols and functions to validate query conditions"
  (:require [cmr.common-app.services.search.query-model :as qm]
            [cmr.common.mime-types :as mt]
            [cmr.common-app.services.search.validators.max-number-of-conditions :as max-conditions]))

(defmulti supported-result-formats
  "Supported search result formats by concept."
  (fn [concept-type]
    concept-type))

(defmethod supported-result-formats :default
  [_]
  ;; Defaults to json unless overriden.
  #{:json})

(defn validate-concept-type-result-format
  "Validate requested search result format for concept type."
  [concept-type result-format]
  (let [mime-type (mt/format->mime-type result-format)
        result-format (if (and (map? result-format) (nil? (:version result-format)))
                        ;; No version was specified so validate just format key
                        (mt/format-key result-format)
                        result-format)]
    (when-not (get (supported-result-formats concept-type) result-format)
      (if-let [version (mt/version-of mime-type)]
        [(format "The mime type [%s] with version [%s] is not supported for %ss."
                 (mt/base-mime-type-of mime-type) version (name concept-type))]
        [(format "The mime type [%s] is not supported for %ss." mime-type (name concept-type))]))))

(defprotocol Validator
  "Defines the protocol for validating query conditions.
  A sequence of errors should be returned if validation fails, otherwise an empty sequence is returned."
  (validate
    [c]
    "Validate condition and return errors if found"))

(defmulti query-validations
  "Returns validation functions for a specific concept type. Each function should take the query and
   return errors if invalid."
  (fn [concept-type]
    concept-type))

(defmethod query-validations :default
  [_]
  nil)

(extend-protocol Validator
  cmr.common_app.services.search.query_model.Query
  (validate
    [{:keys [concept-type result-format condition ] :as query}]
    (let [concept-specific-validations (query-validations concept-type)
          errors (concat (validate-concept-type-result-format concept-type result-format)
                         (mapcat #(% query) concept-specific-validations)
                         (max-conditions/validate query))]
      (if (seq errors) errors (validate condition))))

  cmr.common_app.services.search.query_model.ConditionGroup
  (validate
    [{:keys [conditions]}]
    (mapcat validate conditions))

  ;; catch all validator
  java.lang.Object
  (validate [this] []))
