(ns cmr.search.validators.validation
  "Defines protocols and functions to validate conditions"
  (:require [cmr.search.models.query :as qm]))

(defprotocol Validator
  "Defines the protocol for validating query conditions.
  A sequence of errors should be returned if validation fails, otherwise an empty sequence is returned."
  (validate
    [c]
    "Validate condition and return errors if found"))

(defn validate-query
  "Validate the query"
  [query]
  (validate (:condition query)))

(extend-protocol Validator
  cmr.search.models.query.ConditionGroup
  (validate
    [{:keys [conditions]}]
    (mapcat validate conditions))

  ;; catch all validator
  java.lang.Object
  (validate [this] []))

