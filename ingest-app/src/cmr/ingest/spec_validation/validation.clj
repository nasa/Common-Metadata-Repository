(ns cmr.ingest.spec-validation.validation
  "Provides functions to validate concept"
  (:require
    [cmr.common.services.errors :as errors]
    [cmr.ingest.spec-validation.business-rule-validation :as spec-bv]))

(defn if-errors-throw
  "Throws an error if there are any errors."
  [error-type errors]
  (when (seq errors)
    (errors/throw-service-errors error-type errors)))

(defn validate-umm-spec-business-rules
  "Validates the concept against CMR ingest business rules."
  [context concept]
  (if-errors-throw :invalid-data
                   (mapcat #(% context concept)
                           (spec-bv/business-rule-validations (:concept-type concept)))))
